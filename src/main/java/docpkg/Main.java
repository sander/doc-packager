package docpkg;

import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class Main {

  public static final SemanticVersion minimumContentTrackerVersion =
    new SemanticVersion("git", 2, 37, 0);

  public static boolean checkContentTrackerCompatibility() {
    return getContentTrackerVersion()
      .flatMap(SemanticVersion::from)
      .map(minimumContentTrackerVersion::isMetBy)
      .orElse(false);
  }

  public static void main(String[] args) {
    System.out.printf("%s %s\n", name, getVersion().orElse(defaultVersion));
  }

  private static final String defaultVersion = "(Not packaged)";
  private static final String name = "Documentation Packager";

  private static Optional<String> getContentTrackerVersion() {
    try {
      var process =
        new ProcessBuilder(minimumContentTrackerVersion.name, "version")
          .start();
      var bytes = process.getInputStream().readAllBytes();
      return Optional.of(new String(bytes).trim());
    } catch (IOException e) {
      return Optional.empty();
    }
  }

  private static Optional<String> getVersion() {
    return Optional
      .ofNullable(Main.class.getPackage().getImplementationVersion());
  }

  private record SemanticVersion(String name, int major, int minor, int patch) {

    static Optional<SemanticVersion> from(String s) {
      var pattern =
        Pattern.compile("([^ ]+) version (\\d+)\\.(\\d+)\\.(\\d+) \\(.*\\)");
      return Optional.of(s)
        .map(pattern::matcher)
        .filter(Matcher::matches)
        .map(m -> {
          var components = Stream.of(2, 3, 4)
            .map(m::group)
            .map(Integer::parseUnsignedInt)
            .toList();
          return new SemanticVersion(
            m.group(1),
            components.get(0),
            components.get(1),
            components.get(2)
          );
        });
    }

    boolean isMetBy(SemanticVersion other) {
      return other.isCompatibleWith(this);
    }

    boolean isCompatibleWith(SemanticVersion requirement) {
      return name.equals(requirement.name) &&
        major == requirement.major
        && minor >= requirement.minor
        && (minor > requirement.minor || patch >= requirement.patch);
    }
  }
}
