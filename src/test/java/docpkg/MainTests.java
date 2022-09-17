package docpkg;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainTests {

  @Test
  void runsWithoutErrors() {
    Main.main(new String[]{});
  }

  @Test
  @Tag("integration")
  void detectsContentTrackerAssumingGitIsInstalled() {
    assertTrue(Main.checkContentTrackerCompatibility());
  }

  @Test
  void semanticVersionComparison() {
    var installed = new Main.SemanticVersion("git", 2, 37, 3);
    var requirement = new Main.SemanticVersion("git", 2, 37, 0);
    assertTrue(requirement.isMetBy(installed));
  }

  @Test
  void semanticVersionParsing() {
    Stream.of("git version 2.37.0 (Apple Git-136)", "git version 2.37.0")
      .map(Main.SemanticVersion::from)
      .map(Optional::isPresent)
      .forEach(Assertions::assertTrue);
  }
}
