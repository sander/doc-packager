package docpkg;

import docpkg.ContentTracking.BranchName;
import docpkg.ContentTracking.CommitId;
import docpkg.ContentTracking.CommitMessage;
import docpkg.Design.BoundedContext;
import docpkg.Design.Risk;
import docpkg.SymbolicExpressions.Expression.Atom;
import docpkg.SymbolicExpressions.Expression.Pair;
import docpkg.SymbolicExpressions.Expression.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static docpkg.SymbolicExpressions.Expression.atom;

@BoundedContext
class DocumentationPackaging {

  private static final Logger logger = LoggerFactory.getLogger(DocumentationPackaging.class);

  interface Service {

    void publish(Collection<FileDescription> files);
  }

  record Manifest(PackageId id, PackageName title) {

    static Optional<Manifest> of(SymbolicExpressions.Expression expression) {
      return Optional.of(expression).flatMap(e -> {
        if (e instanceof Pair p) {
          return Optional.of(p);
        } else {
          return Optional.empty();
        }
      }).flatMap(p -> {
        if (p.car().equals(atom("manifest")) && p.cdr() instanceof Pair c) {
          var list = c.toList();
          if (list.size() % 2 != 0) {
            return Optional.empty();
          }
          var pairs = IntStream.range(0, list.size()).boxed().collect(Collectors.groupingBy(e -> e / 2, Collectors.mapping(list::get, Collectors.toList()))).values().stream().collect(Collectors.toMap(l -> l.get(0), l -> l.get(1)));
          var id = pairs.get(atom(":docpkg/id"));
          var name = pairs.get(atom(":docpkg/name"));
          if (id instanceof Atom a && name instanceof Text n) {
            return Optional.of(new Manifest(new PackageId(a.value()), new PackageName(n.value())));
          } else {
            return Optional.empty();
          }
        } else {
          return Optional.empty();
        }
      });
    }
  }

  record FileDescription(Path path) {}

  record PackageId(String value) {

    static Pattern pattern = Pattern.compile("[a-z][a-z-/]{0,19}");

    PackageId {
      Objects.requireNonNull(value);
      if (!pattern.matcher(value).matches()) throw new IllegalArgumentException(String.format("Input did not match %s", pattern.pattern()));
    }
  }

  record PackageName(String value) {}

  @Risk(scenario = "Memory leaks since the work tree is not cleaned up")
  @Risk(scenario = "User data lost when creating a work tree when one exists")
  static class Live implements Service {

    final private ContentTracking.Service content;
    final private Path path = Path.of("target/docpkg");

    Live(ContentTracking.Service content, PackageId name) {
      this.content = content;

      createWorkTree(name);
    }

    @Risk(scenario = "User has the origin configured not as `origin`")
    void ensureBranchExistsWithDefaultCommit(BranchName name, CommitId id) {
      content.createBranch(name, new BranchName(String.format("origin/%s", name.value())));
      content.createBranch(name, id);
    }

    CommitId createInitialCommit() {
      var treeId = content.makeTree();
      logger.debug("Committing tree with hash {}", treeId);
      var commitId = content.commitTree(treeId);
      logger.debug("Created commit ID {}", commitId);
      return commitId;
    }

    void createWorkTree(PackageId name) {
      var branchName = new BranchName(String.format("docpkg/%s", name.value()));
      FileOperations.removeRecursively(path);
      var commitId = createInitialCommit();
      ensureBranchExistsWithDefaultCommit(branchName, commitId);
      content.addWorkTree(path, branchName);
    }

    @Override
    public void publish(Collection<FileDescription> files) {
      logger.debug("Publishing {}", files);
      files.forEach(d -> content.addFile(path, d.path()));
      var commitId = content.commit(path, new CommitMessage("docs: new package"));
      logger.debug("Committed: {}", commitId);
    }
  }
}
