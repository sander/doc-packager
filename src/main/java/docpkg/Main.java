package docpkg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {

  public static final SemanticVersion minimumContentTrackerVersion =
    new SemanticVersion("git", 2, 37, 0);

  public static Optional<ContentTracker> getCompatibleContentTracker() {
    var version = getContentTrackerVersion();
    logger.debug("Got content tracker version: {}", version);

    return version
      .flatMap(SemanticVersion::from).stream()
      .peek(v -> logger.debug("Parsed as semantic version: {}", v))
      .filter(minimumContentTrackerVersion::isMetBy)
      .findFirst()
      .map(v -> new LocalGitContentTracker());
  }

  public static void main(String[] args) {
    System.out.printf("%s version %s\n",
      name, getVersion().orElse(defaultVersion));
  }

  static void addWorkTree(Path path, BranchName name) throws IOException, InterruptedException {
    var worktree = new ProcessBuilder("git", "worktree", "add", "--force", path.toString(), name.value()).start();
    assert (worktree.waitFor() == 0);
  }

  /**
   * Assumes that the origin is called <code>origin</code>.
   *
   * @param name is the name of the branch
   * @param id   is the ID of the commit
   * @throws IOException          if any Git command fails
   * @throws InterruptedException if the thread gets interrupted while waiting
   *                              for Git
   */
  static void ensureBranchExistsWithDefaultCommit(BranchName name, CommitId id)
    throws IOException, InterruptedException {
    var trackingBranch = new ProcessBuilder("git", "branch", name.value(),
      String.format("origin/%s", name.value())).start();
    switch (trackingBranch.waitFor()) {
      case 0, 128 -> {
      }
      default -> throw new RuntimeException(
        String.format("Unexpected error code %d upon branch tracking",
          trackingBranch.exitValue()));
    }
    var newBranch = new ProcessBuilder("git", "branch", name.value(),
      id.hash()).start();
    switch (newBranch.waitFor()) {
      case 0, 128 -> {
      }
      default -> throw new RuntimeException(
        String.format("Unexpected error code %d upon branch creation",
          newBranch.exitValue()));
    }
  }

  /**
   * Assumes POSIX compliance (in particular <code>/dev/null</code>).
   * Assumes Git is configured well (in particular, can commit).
   *
   * @return the ID of the initial commit
   * @throws IOException          if any Git command fails
   * @throws InterruptedException if the thread gets interrupted while waiting
   *                              for Git
   */
  static CommitId createInitialCommit()
    throws IOException, InterruptedException {
    var hashObject = new ProcessBuilder(
      "git", "hash-object", "-t", "tree", "/dev/null").start();
    assert (hashObject.waitFor() == 0);
    var treeId =
      new BufferedReader(new InputStreamReader(hashObject.getInputStream()))
        .lines().collect(Collectors.joining("\n")).trim();
    var commitTree = new ProcessBuilder("git", "commit-tree", treeId, "-m",
      "build: new documentation package").start();
    assert commitTree.waitFor() == 0 :
      String.format("Unexpected error code %d with message:\n%s",
        commitTree.exitValue(),
        new BufferedReader(new InputStreamReader(hashObject.getErrorStream()))
          .lines().collect(Collectors.joining("\n")).trim());
    var commitId =
      new BufferedReader(new InputStreamReader(commitTree.getInputStream()))
        .lines().collect(Collectors.joining("\n")).trim();
    return new CommitId(commitId);
  }

  static void createWorkTree(PackageName name)
    throws IOException, InterruptedException {
    Path path = Path.of("target/docpkg");
    var branchName = new BranchName(String.format("docpkg/%s", name.value()));
    removeRecursively(path);
    var commitId = createInitialCommit();
    ensureBranchExistsWithDefaultCommit(branchName, commitId);
    addWorkTree(path, branchName);
  }

  static void removeRecursively(Path path) throws IOException {
    if (Files.exists(path)) {
      try (var walk = Files.walk(path)) {
        for (var p : walk.sorted(Comparator.reverseOrder()).toList()) {
          Files.delete(p);
        }
      }
    }
  }

  record BranchName(String value) {
  }

  record CommitId(String hash) {
  }

  interface ContentTracker {
  }

  record PackageName(String value) {
  }

  record SemanticVersion(String name, int major, int minor, int patch) {

    static Optional<SemanticVersion> from(String s) {
      var pattern = Pattern.compile(
        "([^ ]+) version (\\d+)\\.(\\d+)\\.(\\d+)(?: \\(.*\\))?"
      );
      return Optional.of(s)
        .map(pattern::matcher)
        .filter(Matcher::matches)
        .map(m -> {
          var components = Stream.of(2, 3, 4)
            .map(m::group)
            .map(Integer::parseUnsignedInt)
            .toList();
          return new SemanticVersion(
            m.group(1),
            components.get(0),
            components.get(1),
            components.get(2)
          );
        });
    }

    boolean isMetBy(SemanticVersion other) {
      return other.isCompatibleWith(this);
    }

    private boolean isCompatibleWith(SemanticVersion requirement) {
      return name.equals(requirement.name) &&
        major == requirement.major
        && minor >= requirement.minor
        && (minor > requirement.minor || patch >= requirement.patch);
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final String defaultVersion = "(Not packaged)";
  private static final String name = "Documentation Packager";

  private static Optional<String> getContentTrackerVersion() {
    try {
      var process =
        new ProcessBuilder(minimumContentTrackerVersion.name, "version")
          .start();
      var bytes = process.getInputStream().readAllBytes();
      return Optional.of(new String(bytes).trim());
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static Optional<String> getVersion() {
    return Optional
      .ofNullable(Main.class.getPackage().getImplementationVersion());
  }

  private static class LocalGitContentTracker implements ContentTracker {
  }
}
