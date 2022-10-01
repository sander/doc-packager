package docpkg;

import docpkg.DocumentationPackaging.PackageName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DocumentationPackagingTests {

  static PackageName name = new PackageName("main");

  @Test
  @Tag("integration")
  void createsWorkTree() {
    var content = new ContentTracking.GitService();
    var packaging = new DocumentationPackaging.Live(content);
    packaging.createWorkTree(name);
  }

  @Test
  void validatePackageNames() {
    Stream.of("a", "a/b", "a-b").forEach(PackageName::new);
    assertThrows(NullPointerException.class, () -> new PackageName(null));
    Stream.of("", "A", "a".repeat(256), "-", "a:b").forEach(s ->
        assertThrows(IllegalArgumentException.class,
            () -> new PackageName(s)));
  }
}
