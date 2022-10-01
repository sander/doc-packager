package docpkg;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MainTests {

  static Main.PackageName name = new Main.PackageName("main");

  @Test
  void runsWithoutErrors() {
    Main.main(new String[]{});
  }

  @Test
  @Tag("integration")
  void createsWorkTree() {
    var content = new ContentTracking.GitService();
    var main = new Main(content);
    main.createWorkTree(name);
  }

  @Test
  void validatePackageNames() {
    Stream.of("a", "a/b", "a-b").forEach(Main.PackageName::new);
    assertThrows(NullPointerException.class, () -> new Main.PackageName(null));
    Stream.of("", "A", "a".repeat(256), "-", "a:b").forEach(s ->
        assertThrows(IllegalArgumentException.class,
            () -> new Main.PackageName(s)));
  }
}
