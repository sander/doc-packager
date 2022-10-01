package docpkg;

import docpkg.ContentTracking.BranchName;
import docpkg.ContentTracking.CommitId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

public class Main {

  final ContentTracking.Service content;

  Main(ContentTracking.Service content) {
    this.content = content;
  }

  public static void main(String[] args) {
    System.out.printf("%s version %s\n",
        name, getVersion().orElse(defaultVersion));
  }

  /**
   * Assumes that the origin is called <code>origin</code>.
   */
  void ensureBranchExistsWithDefaultCommit(BranchName name,
                                           CommitId id) {
    content.createBranch(name,
        new BranchName(
            String.format("origin/%s", name.value())));
    content.createBranch(name, id);
  }

  /**
   * Assumes POSIX compliance (in particular <code>/dev/null</code>).
   * Assumes Git is configured well (in particular, can commit).
   */
  CommitId createInitialCommit() {
    var treeId = content.makeTree();
    logger.debug("Committing tree with hash {}", treeId);
    var commitId = content.commitTree(treeId);
    logger.debug("Created commit ID {}", commitId);
    return commitId;
  }

  void createWorkTree(PackageName name) {
    Path path = Path.of("target/docpkg");
    var branchName = new BranchName(
        String.format("docpkg/%s", name.value()));
    removeRecursively(path);
    var commitId = createInitialCommit();
    ensureBranchExistsWithDefaultCommit(branchName, commitId);
    content.addWorkTree(path, branchName);
  }

  static void removeRecursively(Path path) {
    if (Files.exists(path)) {
      try (var walk = Files.walk(path)) {
        for (var p : walk.sorted(Comparator.reverseOrder()).toList()) {
          try {
            Files.delete(p);
          } catch (IOException e) {
            throw new RuntimeException("Could not delete", e);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException("Could not walk", e);
      }
    }
  }

  record PackageName(String value) {

    static Pattern pattern = Pattern.compile("[a-z][a-z-/]{0,19}");

    PackageName {
      Objects.requireNonNull(value);
      if (!pattern.matcher(value).matches())
        throw new IllegalArgumentException(
            String.format("Input did not match %s", pattern.pattern()));
    }
  }

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final String defaultVersion = "(Not packaged)";
  private static final String name = "Documentation Packager";

  private static Optional<String> getVersion() {
    return Optional
        .ofNullable(Main.class.getPackage().getImplementationVersion());
  }
}
