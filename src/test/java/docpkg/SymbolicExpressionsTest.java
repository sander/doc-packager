package docpkg;

import docpkg.SymbolicExpressions.Expression.Atom;
import docpkg.SymbolicExpressions.Expression.StringAtom;
import docpkg.SymbolicExpressions.Expression.SymbolicList;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;
import java.util.Optional;

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
    assertEquals(Optional.of(SymbolicList.empty()), read(new StringReader(input)));
  }

  @Test
  void testParse() {
    // Example input from https://rosettacode.org/wiki/S-expressions
    var input = "((data \"quoted data\" 123 4.5)\n" + " (data (!@# (4.5) \"(more\" \"data)\")))";

    var result = SymbolicExpressions.read(new StringReader(input));

    var data1 = new SymbolicList(List.of(new Atom("data"), new StringAtom("quoted data"), new Atom("123"), new Atom("4.5")));
    var data2 = new SymbolicList(List.of(new Atom("data"), new SymbolicList(List.of(new Atom("!@#"), new SymbolicList(List.of(new Atom("4.5"))), new StringAtom("(more"), new StringAtom("data)")))));

    assertEquals(Optional.of(new SymbolicList(List.of(data1, data2))), result);
  }
}
