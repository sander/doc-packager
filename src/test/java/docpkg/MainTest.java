package docpkg;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

public class MainTest {

  @Test
  void testRun() {
    Main.main(new String[]{});
  }

  @Test
  @Tag("integration")
  void testPublish() {
    Main.main(new String[]{"publish"});
  }
}
