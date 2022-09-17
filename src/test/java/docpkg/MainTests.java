package docpkg;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
}
