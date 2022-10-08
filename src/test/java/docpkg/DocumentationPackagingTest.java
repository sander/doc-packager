package docpkg;

import docpkg.DocumentationPackaging.FileDescription;
import docpkg.DocumentationPackaging.PackageName;
import docpkg.DocumentationPackaging.Service;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class DocumentationPackagingTest {

  private static final Logger logger =
      LoggerFactory.getLogger(DocumentationPackagingTest.class);

  final PackageName name = new PackageName("main");
  final ContentTracking.Service content = new ContentTracking.GitService();

  static final Path testDirectory = Path.of("target/test-packaging");
  static final Path origin = testDirectory.resolve("origin");
  static final Path clone = testDirectory.resolve("clone");

  @BeforeEach
  void setUp() {
    var service = new ContentTracking.GitService();
    var resource = Objects.requireNonNull(
        getClass().getClassLoader().getResource("docpkg/example"));
    var source = Path.of(resource.getPath());

    FileOperations.removeRecursively(testDirectory);
    FileOperations.copyRecursively(source, origin);
    service.initialize(origin);
    service.addCurrentWorktree(origin);
    var maybeId = service.commit(origin,
        new ContentTracking.CommitMessage("feat: initial commit"));
    assert maybeId.isPresent();
    service.clone(origin, clone);
  }

  @AfterEach
  void tearDown() {
    FileOperations.removeRecursively(testDirectory);
  }

  @Test
  @Tag("integration")
  void testInitialization() {
    new DocumentationPackaging.Live(content, name);
  }

  @Test
  void testPackageNameValidation() {
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
  void testPublishing() {
    Service service = new DocumentationPackaging.Live(content, name);
    service.publish(Set.of(
        getResourceFileDescription("docpkg/example/document.md"),
        getResourceFileDescription("docpkg/example/document-2.md")
    ));
  }
}
