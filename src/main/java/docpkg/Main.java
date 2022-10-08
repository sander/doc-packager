package docpkg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class Main {

  public static void main(String[] args) {
    if (args.length == 0) {
      System.out.printf("%s version %s\n",
          name, getVersion().orElse(defaultVersion));
    } else {
      switch (args[0]) {
        case "publish" -> {
          if (args.length == 1) {
            logger.debug("Invoking the publish command");
          } else {
            System.err.println("Too many arguments");
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

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final String defaultVersion = "(Not packaged)";
  private static final String name = "Documentation Packager";

  private static Optional<String> getVersion() {
    return Optional
        .ofNullable(Main.class.getPackage().getImplementationVersion());
  }
}
