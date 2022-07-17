package nl.sanderdijkhuis.docpkg

import Traversal.*

import munit.Assertions.*

class TraversalSuite extends munit.FunSuite:

  type A = Error[Unit] | Result[Unit]

  test("raises errors") {
    assert(List(Error(())).sequence == Error(()))
    assert(List[A](Error(()), Result(())).sequence == Error(()))
    assert(List[A](Result(()), Error(())).sequence == Error(()))
    assert(List[A](Error(()), Result(()), Error(())).sequence == Error(()))
  }

  test("returns lists if there are no errors") {
    assertEquals(List[A](Result(()), Result(())).sequence, Result(List((), ())))
  }
