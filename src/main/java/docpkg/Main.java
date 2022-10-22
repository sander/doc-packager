package docpkg;

import docpkg.DocumentationPackaging.Manifest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final String defaultVersion = "(Not packaged)";
  private static final String name = "Documentation Packager";

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.printf("%s version %s\n", name, getVersion().orElse(defaultVersion));
    } else {
      switch (args[0]) {
        case "publish" -> {
          if (args.length == 2) {
            logger.debug("Invoking the publish command");
            var path = Path.of(args[1]);
            try (var reader = new FileReader(path.resolve(".docpkg").toFile())) {
              var manifest = SymbolicExpressions.read(reader).flatMap(Manifest::of);
              logger.debug("Manifest: {}", manifest);
              var content = new ContentTracking.GitService();
              if (manifest.isPresent()) {
                try (var packaging = new DocumentationPackaging.Live(content, path, manifest.get().id())) {
                  packaging.publish(manifest.get().files());
                }
              } else {
                System.err.println("Invalid manifest");
              }
            } catch (IOException e) {
              System.err.println("Could not read manifest");
            }
          } else {
            System.err.println("Specify the path");
            System.exit(1);
          }
        }
        default -> {
          System.err.println("Invalid command");
          System.exit(1);
        }
      }
    }
  }

  private static Optional<String> getVersion() {
    return Optional.ofNullable(Main.class.getPackage().getImplementationVersion());
  }
}
