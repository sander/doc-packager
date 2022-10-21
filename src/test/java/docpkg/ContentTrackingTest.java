package docpkg;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ContentTrackingTest {

  @Test
  @Tag("integration")
  void testInitializationAssumingGitIsInstalled() {
    new ContentTracking.GitService();
  }

  @Test
  void testSemanticVersionComparison() {
    var installed = new ContentTracking.SemanticVersion("git", 2, 37, 3);
    var requirement = new ContentTracking.SemanticVersion("git", 2, 37, 0);
    assertTrue(requirement.isMetBy(installed));
  }

  @Test
  void testSemanticVersionParsing() {
    Stream.of("git version 2.37.0 (Apple Git-136)", "git version 2.37.0")
        .map(ContentTracking.SemanticVersion::from)
        .map(Optional::isPresent)
        .forEach(Assertions::assertTrue);
  }

  @Test
  void testGetBranchName() {
    var path = Path.of("target/test-tracking");
    var content = new ContentTracking.GitService();
    FileOperations.removeRecursively(path);
    try {
      Files.createDirectories(path);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    content.initialize(path);
    var name = content.getCurrentBranchName(path);
    assertEquals("main", name.value());
  }
}
