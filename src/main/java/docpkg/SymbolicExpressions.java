package docpkg;

import java.util.List;
import java.util.Optional;

public class SymbolicExpressions {

  static Optional<Expression> read(String input) {
    if (input.equals("()")) {
      return Optional.of(Expression.SymbolicList.empty());
    }
    return Optional.empty();
  }

  sealed interface Expression {

    record SymbolicList(List<Expression> value) implements Expression {

      static SymbolicList empty() {
        return new SymbolicList(List.of());
      }
    }
  }
}
