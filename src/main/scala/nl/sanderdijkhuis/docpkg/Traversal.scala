package nl.sanderdijkhuis.docpkg

object Traversal:

  case class Error[A](value: A)
  case class Result[A](value: A)

  extension [A, B](l: List[Error[A] | Result[B]])
    def sequence: Error[A] | Result[List[B]] = l match
      case Nil           => Result(Nil)
      case Error(a) :: _ => Error(a)
      case Result(b) :: t =>
        t.sequence match
          case Error(a)     => Error(a)
          case Result(list) => Result(b :: list)
