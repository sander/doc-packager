package docpkg;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StreamTokenizer;
import java.util.*;

public class SymbolicExpressions {

  static Optional<Expression> read(Reader reader) {
    return parse(tokenize(reader));
  }

  static List<Token> tokenize(Reader reader) {
    var tokenizer = new StreamTokenizer(new BufferedReader(reader));
    tokenizer.resetSyntax();
    tokenizer.whitespaceChars(0, ' ');
    tokenizer.wordChars(' ' + 1, 255);
    tokenizer.ordinaryChar('(');
    tokenizer.ordinaryChar(')');
    tokenizer.ordinaryChar('\'');
    tokenizer.commentChar(';');
    tokenizer.quoteChar('"');

    var result = new LinkedList<Token>();
    while (true) {
      try {
        tokenizer.nextToken();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      if (tokenizer.ttype == StreamTokenizer.TT_EOF) {
        return result;
      } else {
        result.add(new Token(tokenizer.ttype, tokenizer.sval, tokenizer.lineno()));
      }
    }
  }

  static Optional<Expression> parse(List<Token> input) {
    return parsePart(input).filter(result -> result.rest().isEmpty()).map(ParseResult::expression);
  }

  private static Optional<ParseResult> parsePart(List<Token> input) {
    var token = input.get(0);
    var rest = input.subList(1, input.size());
    return switch (token.type) {
      case '(' -> parseList(rest);
      case '"' -> Optional.of(new ParseResult(new Expression.Text(token.text), rest));
      case StreamTokenizer.TT_WORD -> Optional.of(new ParseResult(new Expression.Atom(token.text), rest));
      default -> throw new RuntimeException(String.format("Unknown token type %d", token.type));
    };
  }

  private static Optional<ParseResult> parseList(List<Token> input) {
    var result = new LinkedList<Expression>();
    var rest = input;
    if (rest.isEmpty()) {
      return Optional.empty();
    }
    while (rest.get(0).type != ')') {
      var element = parsePart(rest);
      if (element.isEmpty()) return Optional.empty();
      result.add(element.get().expression());
      rest = element.get().rest();
      if (rest.isEmpty()) {
        return Optional.empty();
      }
    }
    var expression = result.size() == 0 ? Expression.nil : Expression.list(result.get(0), result.subList(1, result.size()).toArray(Expression[]::new));
    return Optional.of(new ParseResult(expression, rest.subList(1, rest.size())));
  }

  sealed interface Expression {

    Nil nil = new Nil();

    static Pair list(Expression head, Expression... tail) {
      if (tail.length == 0) {
        return new Pair(head, new Nil());
      } else {
        return new Pair(head, list(tail[0], Arrays.copyOfRange(tail, 1, tail.length)));
      }
    }

    static Atom atom(String value) {
      return new Atom(value);
    }

    static Text text(String value) {
      return new Text(value);
    }

    record Pair(Expression car, Expression cdr) implements Expression {

      public Pair {
        Objects.requireNonNull(car);
        Objects.requireNonNull(cdr);
      }

      public List<Expression> toList() {
        if (cdr.equals(nil)) {
          return new ArrayList<Expression>(List.of(car));
        } else if (cdr instanceof Pair p) {
          var rest = p.toList();
          rest.add(0, car);
          return rest;
        } else {
          throw new RuntimeException("Not a list");
        }
      }
    }

    record Nil() implements Expression {}

    record Text(String value) implements Expression {}

    record Atom(String value) implements Expression {}
  }

  record ParseResult(Expression expression, List<Token> rest) {}

  record Token(int type, String text, int line) {}
}
