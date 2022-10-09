package docpkg;

import docpkg.SymbolicExpressions.Expression.Atom;
import docpkg.SymbolicExpressions.Expression.Text;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.Optional;

import static docpkg.SymbolicExpressions.Expression.list;
import static docpkg.SymbolicExpressions.Expression.nil;
import static docpkg.SymbolicExpressions.read;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SymbolicExpressionsTest {

  @Test
  void testInvalidInput() {
    var input = "(";
    assertEquals(Optional.empty(), read(new StringReader(input)));
  }

  @Test
  void testEmptyList() {
    var input = "()";
    assertEquals(Optional.of(nil), read(new StringReader(input)));
  }

  @Test
  void testParse() {
    // Example input from https://rosettacode.org/wiki/S-expressions
    var input = "((data \"quoted data\" 123 4.5)\n" + " (data (!@# (4.5) \"(more\" \"data)\")))";

    var result = SymbolicExpressions.read(new StringReader(input));

    var data1 = list(new Atom("data"), new Text("quoted data"), new Atom("123"), new Atom("4.5"));
    var data2 = list(new Atom("data"), list(new Atom("!@#"), list(new Atom("4.5")), new Text("(more"), new Text("data)")));

    assertEquals(Optional.of(list(data1, data2)), result);
  }
}
