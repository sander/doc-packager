package nl.sanderdijkhuis.docpkg

object Traversal:

  case class Error[A](value: A)
  case class Ok[A](value: A)

  type Result[A, B] = Error[A] | Ok[B]

  extension [A, B](l: List[Result[A, B]])
    def sequence: Result[A, List[B]] = l match
      case Nil           => Ok(Nil)
      case Error(a) :: _ => Error(a)
      case Ok(b) :: t =>
        t.sequence match
          case Error(a) => Error(a)
          case Ok(list) => Ok(b :: list)

  extension [A, B](r: Result[A, B])
    def toOption: Option[B] = r match
      case Error(_) => None
      case Ok(b)    => Some(b)

    def error: Option[A] = r match
      case Error(a) => Some(a)
      case Ok(_)    => None

    def flatMap[C](f: B => Result[A, C]): Result[A, C] = r match
      case e @ Error(_) => e
      case Ok(b)        => f(b)

    def map[C](f: B => C): Result[A, C] = r.flatMap(b => Ok(f(b)))
