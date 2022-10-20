package docpkg;

import docpkg.ContentTracking.CommitMessage;
import docpkg.DocumentationPackaging.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

import static docpkg.SymbolicExpressions.Expression.*;
import static java.util.Objects.requireNonNull;
import static org.junit.jupiter.api.Assertions.*;

public class DocumentationPackagingTest {

  final PackageId name = new PackageId("main");
  final ContentTracking.Service content = new ContentTracking.GitService();

  final Path testDirectory = Path.of("target/test-packaging");
  final Path origin = testDirectory.resolve("origin");
  final Path clone = testDirectory.resolve("clone");

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
  @Tag("integration")
  void testInitialization() {
    new DocumentationPackaging.Live(content, name);
  }

  @Test
  void testPackageNameValidation() {
    Stream.of("a", "a/b", "a-b").forEach(PackageId::new);
    assertThrows(NullPointerException.class, () -> new PackageId(null));

    var illegal = Stream.of("", "A", "a".repeat(256), "-", "a:b");
    illegal.forEach(s -> assertThrows(IllegalArgumentException.class, () -> new PackageId(s)));
  }

  @Test
  @Tag("integration")
  void testPublishing() {
    Service service = new DocumentationPackaging.Live(content, name);
    var descriptions = Set.of(getResourceFileDescription("docpkg/example/document.md"), getResourceFileDescription("docpkg/example/document-2.md"));
    service.publish(descriptions);
  }

  @Test
  void testManifestParsing() {
    var in = list(
        atom("manifest"),
        atom(":id"), atom("id"),
        atom(":name"), text("Name"),
        atom(":paths"), list(text("path1"), text("path2/sub-path"))
    );
    var result = Manifest.of(in);
    assertTrue(result.isPresent());
    assertEquals(result.get(), new Manifest(new PackageId("id"), new PackageName("Name"), Set.of(new FileDescription(Path.of("path1")), new FileDescription(Path.of("path2/sub-path")))));
  }

  private Path getResource(String name) {
    var url = requireNonNull(getClass().getClassLoader().getResource(name));
    return Path.of(url.getPath());
  }

  private FileDescription getResourceFileDescription(String s) {
    var workingDirectory = Paths.get("").toAbsolutePath();
    var relativePath = workingDirectory.relativize(getResource(s));
    return new DocumentationPackaging.FileDescription(relativePath);
  }
}
