package docpkg;

import static java.util.Objects.requireNonNullElse;

public class Main {

  private static final String name = "Documentation Packager";

  public static void main(String[] args) {
    var p = Main.class.getPackage();
    var v = requireNonNullElse(p.getImplementationVersion(), "(Not packaged)");
    System.out.printf("%s %s\n", name, v);
  }
}
