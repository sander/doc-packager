package docpkg;

import docpkg.SymbolicExpressions.Expression.SymbolicList;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static docpkg.SymbolicExpressions.read;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SymbolicExpressionsTest {

  @Test
  void testInvalidInput() {
    var input = "(";
    assertEquals(Optional.empty(), read(input));
  }

  @Test
  void testEmptyList() {
    var input = "()";
    assertEquals(Optional.of(SymbolicList.empty()), read(input));
  }
}
