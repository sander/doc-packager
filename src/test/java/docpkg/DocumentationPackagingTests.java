package docpkg;

import docpkg.DocumentationPackaging.FileDescription;
import docpkg.DocumentationPackaging.PackageName;
import docpkg.DocumentationPackaging.Service;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DocumentationPackagingTests {

  private static final Logger logger =
      LoggerFactory.getLogger(DocumentationPackagingTests.class);

  final PackageName name = new PackageName("main");
  final ContentTracking.Service content = new ContentTracking.GitService();

  @Test
  @Tag("integration")
  void createsWorkTree() {
    new DocumentationPackaging.Live(content, name);
  }

  @Test
  void validatePackageNames() {
    Stream.of("a", "a/b", "a-b").forEach(PackageName::new);
    assertThrows(NullPointerException.class, () -> new PackageName(null));
    Stream.of("", "A", "a".repeat(256), "-", "a:b").forEach(s ->
        assertThrows(IllegalArgumentException.class,
            () -> new PackageName(s)));
  }

  private FileDescription getResourceFileDescription(String s) {
    var url = getClass().getClassLoader().getResource(s);
    assert url != null;
    var workingDirectory = Paths.get("").toAbsolutePath();
    var absolutePath = Path.of(url.getPath());
    var relativePath = workingDirectory.relativize(absolutePath);
    return new DocumentationPackaging.FileDescription(relativePath);
  }

  @Test
  @Tag("integration")
  void addsFiles() {
    Service service = new DocumentationPackaging.Live(content, name);
    service.publish(Set.of(
        getResourceFileDescription("docpkg/example/document.md"),
        getResourceFileDescription("docpkg/example/document-2.md")
    ));
  }
}
