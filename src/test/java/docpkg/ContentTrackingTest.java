package docpkg;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

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
}
