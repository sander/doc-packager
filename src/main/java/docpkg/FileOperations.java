package docpkg;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
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

  static void copyRecursively(Path from, Path to) {
    try {
      Files.walkFileTree(from, new CopyVisitor(from, to));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static class CopyVisitor extends SimpleFileVisitor<Path> {
    final Path from;
    final Path to;

    CopyVisitor(Path from, Path to) {
      this.from = from;
      this.to = to;
    }

    @Override
    public FileVisitResult preVisitDirectory(
        Path dir, BasicFileAttributes attrs) throws IOException {
      Files.createDirectories(transform(dir));
      return FileVisitResult.CONTINUE;
    }

    @Override
    public FileVisitResult visitFile(
        Path file, BasicFileAttributes attrs) throws IOException {
      Files.copy(file, transform(file), StandardCopyOption.COPY_ATTRIBUTES);
      return FileVisitResult.CONTINUE;
    }

    private Path transform(Path path) {
      return to.resolve(from.relativize(path));
    }
  }
}
