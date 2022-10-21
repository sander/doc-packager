package docpkg;

import docpkg.DocumentationPackaging.FileDescription;
import docpkg.DocumentationPackaging.PackageId;

import java.nio.file.Path;
import java.util.Collection;

public class DocumentationPackager {

  /**
   * Publishes specified paths to Git branch origin/docpkg/name/branch-name.
   */
  public static void publish(String name, Collection<String> paths) {
    var tracking = new ContentTracking.GitService();
    try (var packaging = new DocumentationPackaging.Live(tracking, Path.of("."), new PackageId(name))) {
      packaging.publish(paths.stream().map(s -> new FileDescription(Path.of(s))).toList());
    }
  }
}
