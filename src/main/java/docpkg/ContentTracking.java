package docpkg;

import docpkg.Design.BoundedContext;
import docpkg.Design.Risk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@BoundedContext
class ContentTracking {

  private static final Logger logger = LoggerFactory.getLogger(ContentTracking.class);

  interface Service {

    void initialize(Path worktree);

    @Risk(scenario = "Origin could be anything, not per se a valid WorkTree")
    void clone(Path origin, Path worktree);

    void addFile(Path worktree, Path sourcePath, Path targetPath);

    void addCurrentWorktree(Path worktree);

    void addWorkTree(Path worktree, Path path, BranchName name);

    void removeWorkTree(Path worktree, Path path);

    BranchName getCurrentBranchName(Path worktree);

    void createBranch(Path worktree, BranchName name, Point point);

    @Risk(scenario = "The Path could be anything, not per se a valid WorkTree")
    Optional<CommitId> commit(Path worktree, CommitMessage message);

    CommitId commitTree(Path worktree, ObjectName name);

    ObjectName makeTree(Path worktree);
  }

  sealed interface Point {

    String value();
  }

  record BranchName(String value) implements Point {}

  record CommitId(String value) implements Point {}

  record CommitMessage(String value) {}

  record ObjectName(String value) {}

  record SemanticVersion(String name, int major, int minor, int patch) {

    static Optional<SemanticVersion> from(String s) {
      var pattern = Pattern.compile("([^ ]+) version (\\d+)\\.(\\d+)\\.(\\d+)(?: \\(.*\\))?");
      return Optional.of(s).map(pattern::matcher).filter(Matcher::matches).map(m -> {
        var components = Stream.of(2, 3, 4).map(m::group).map(Integer::parseUnsignedInt).toList();
        return new SemanticVersion(m.group(1), components.get(0), components.get(1), components.get(2));
      });
    }

    @Override
    public String toString() {
      return String.format("%s %d.%d.%d", name, major, minor, patch);
    }

    boolean isMetBy(SemanticVersion other) {
      return other.isCompatibleWith(this);
    }

    private boolean isCompatibleWith(SemanticVersion requirement) {
      var nameMatches = name.equals(requirement.name);
      var compatibleDesign = major == requirement.major && minor >= requirement.minor;
      return nameMatches && compatibleDesign && (minor > requirement.minor || patch >= requirement.patch);
    }
  }

  static class GitService implements Service {

    public static final SemanticVersion minimumVersion = new SemanticVersion("git", 2, 37, 0);

    public GitService() {
      if (getVersion().stream().peek(v -> logger.debug("Parsed as semantic version: {}", v)).noneMatch(minimumVersion::isMetBy)) {
        throw new RuntimeException("Need " + minimumVersion);
      }
    }

    @Override
    public void initialize(Path worktree) {
      await(command("init", worktree.toString())).expectSuccess();
    }

    @Override
    public void clone(Path origin, Path worktree) {
      await(command("clone", origin.toString(), worktree.toString())).expectSuccess();
    }

    @Override
    public void addFile(Path worktree, Path sourcePath, Path targetPath) {
      var target = worktree.resolve(targetPath);
      logger.debug("Copying from {} to {}", sourcePath, target);
      try {
        Files.createDirectories(target.getParent());
      } catch (IOException e) {
        throw new RuntimeException("Could not create directories", e);
      }
      try {
        Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        throw new RuntimeException("Could not copy file", e);
      }
      logger.debug("Copied");
      await(command("add", targetPath.toString()).directory(worktree.toFile())).expectSuccess();
      logger.debug("Added");
    }

    @Override
    public void addCurrentWorktree(Path worktree) {
      await(command("add", ".").directory(worktree.toFile())).expectSuccess();
    }

    @Override
    public void addWorkTree(Path worktree, Path path, BranchName name) {
      await(command("worktree", "add", "--force", path.toString(), name.value()).directory(worktree.toFile())).expectSuccess();
    }

    @Override
    public void removeWorkTree(Path worktree, Path path) {
      await(command("worktree", "remove", path.toString()).directory(worktree.toFile())).expectSuccess();
    }

    @Override
    public BranchName getCurrentBranchName(Path worktree) {
      return new BranchName(await(command("branch", "--show-current")).get().message());
    }

    @Override
    @Risk(scenario = "Git not configured yet for committing")
    public CommitId commitTree(Path worktree, ObjectName name) {
      var message = "build: new documentation package";
      var command = command("commit-tree", name.value(), "-m", message).directory(worktree.toFile());
      return new CommitId(await(command).get().message());
    }

    @Override
    public void createBranch(Path worktree, BranchName name, Point point) {
      await(command("branch", name.value(), point.value()).directory(worktree.toFile()));
    }

    @Override
    public Optional<CommitId> commit(Path worktree, CommitMessage message) {
      var result = await(command("commit", "-m", message.value()).directory(worktree.toFile()));
      switch (result) {
        case Result.Success s -> {
          var id = new CommitId(await(command("rev-parse", "HEAD")).get().message());
          logger.debug("Committed {}", id);
          return Optional.of(id);
        }
        case Result.Failed f -> {
          logger.debug("Commit failed: {}", f.message().replace("\n", "\\n"));
          return Optional.empty();
        }
        default -> throw new RuntimeException(String.format("Unexpected commit result: %s", result));
      }
    }

    @Override
    @Risk(scenario = "User has no POSIX-compliant /dev/null")
    public ObjectName makeTree(Path worktree) {
      var nullDevice = Path.of("/dev/null").toFile();
      var command = command("mktree").redirectInput(nullDevice).directory(worktree.toFile());
      return new ObjectName(await(command).get().message());
    }

    private <T> List<T> cons(T head, List<T> tail) {
      var result = new LinkedList<T>(tail);
      result.addFirst(head);
      return result;
    }

    private Process start(ProcessBuilder builder) {
      try {
        return builder.start();
      } catch (IOException e) {
        throw new RuntimeException("Error starting command", e);
      }
    }

    private ProcessBuilder command(String... args) {
      String programName = "git";
      var command = cons(programName, List.of(args));
      return new ProcessBuilder(command);
    }

    private String read(InputStream stream) {
      try {
        var bytes = stream.readAllBytes();
        return new String(bytes).trim();
      } catch (IOException e) {
        throw new RuntimeException("Could not read stream", e);
      }
    }

    private Result await(ProcessBuilder builder) {
      var process = start(builder);
      try {
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
          throw new RuntimeException("Process took too long");
        }
      } catch (InterruptedException e) {
        throw new RuntimeException("Interrupted while awaiting process", e);
      }
      try (var stdout = process.getInputStream(); var stderr = process.getErrorStream()) {
        switch (process.exitValue()) {
          case 0 -> {
            return new Result.Success(read(stdout));
          }
          case 1 -> {
            return new Result.Failed(read(stdout));
          }
          case 128 -> {
            return new Result.FatalApplicationError(read(stderr));
          }
          default -> {
            var message = String.format("""
                Unexpected error code %d

                Standard output:
                %s

                Standard error:
                %s""", process.exitValue(), read(stdout), read(stderr));
            throw new RuntimeException(message);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException("Could not read process stream", e);
      }
    }

    Optional<SemanticVersion> getVersion() {
      try {
        return SemanticVersion.from(await(command("version")).get().message());
      } catch (RuntimeException e) {
        return Optional.empty();
      }
    }

    private sealed interface Result {

      default Success get() {
        if (this instanceof Success s) {
          return s;
        } else {
          throw new RuntimeException("Success expectation not met");
        }
      }

      default void expectSuccess() {
        if (!(this instanceof Success)) {
          logger.debug("Result: {}", this);
          throw new RuntimeException("Success expectation not met");
        }
      }

      record Success(String message) implements Result {}

      record FatalApplicationError(String message) implements Result {}

      record Failed(String message) implements Result {}
    }
  }
}
