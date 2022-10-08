package docpkg;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

public class FileOperations {

  static void removeRecursively(Path path) {
    if (Files.exists(path)) {
      try (var walk = Files.walk(path)) {
        for (var p : walk.sorted(Comparator.reverseOrder()).toList()) {
          try {
            Files.delete(p);
          } catch (IOException e) {
            throw new RuntimeException("Could not delete", e);
          }
        }
      } catch (IOException e) {
        throw new RuntimeException("Could not walk", e);
      }
    }
  }
}
