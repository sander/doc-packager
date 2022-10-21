package docpkg;

import docpkg.ContentTracking.CommitMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static java.util.Objects.requireNonNull;

public class MainTest {

  final Path testDirectory = Path.of("target/test-packaging");
  final Path origin = testDirectory.resolve("origin");
  final Path clone = testDirectory.resolve("clone");

  final ContentTracking.Service content = new ContentTracking.GitService();

  final CommitMessage initialCommitMessage = new CommitMessage("feat: initial commit");

  @BeforeEach
  void setUp() {
    var source = getResource("docpkg/example");

    FileOperations.removeRecursively(testDirectory);
    FileOperations.copyRecursively(source, origin);
    content.initialize(origin);
    content.addCurrentWorktree(origin);
    assert content.commit(origin, initialCommitMessage).isPresent();
    content.clone(origin, clone);
  }

  @AfterEach
  void tearDown() {
    FileOperations.removeRecursively(testDirectory);
  }

  @Test
  void testRun() {
    Main.main(new String[]{});
  }

  @Test
  @Tag("integration")
  void testPublish() {
    Main.main(new String[]{"publish", clone.toString()});
  }

  private Path getResource(String name) {
    var url = requireNonNull(getClass().getClassLoader().getResource(name));
    return Path.of(url.getPath());
  }
}
