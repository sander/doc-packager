package docpkg;

import docpkg.DocumentationPackaging.FileDescription;
import docpkg.DocumentationPackaging.PackageId;

import java.nio.file.Path;
import java.util.Arrays;

public class DocumentationPackager {

  /**
   * Publishes specified paths to Git branch origin/docpkg/name/branch-name.
   */
  public static void publish(String name, String... paths) {
    var tracking = new ContentTracking.GitService();
    try (var packaging = new DocumentationPackaging.Live(tracking, Path.of("."), new PackageId(name))) {
      packaging.publish(Arrays.stream(paths).map(s -> new FileDescription(Path.of(s))).toList());
    }
  }
}
