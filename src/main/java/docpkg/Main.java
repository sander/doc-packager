package docpkg;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

public class Main {

  public static void main(String[] args) {
    System.out.printf("%s version %s\n",
        name, getVersion().orElse(defaultVersion));
  }

  private static final Logger logger = LoggerFactory.getLogger(Main.class);
  private static final String defaultVersion = "(Not packaged)";
  private static final String name = "Documentation Packager";

  private static Optional<String> getVersion() {
    return Optional
        .ofNullable(Main.class.getPackage().getImplementationVersion());
  }
}
