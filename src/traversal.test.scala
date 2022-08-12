package docpkg

import Traversal.*

import munit.Assertions.*

class TraversalSuite extends munit.FunSuite:

  type A = Error[Unit] | Ok[Unit]

  test("raises errors") {
    assert(List(Error(())).sequence == Error(()))
    assert(List[A](Error(()), Ok(())).sequence == Error(()))
    assert(List[A](Ok(()), Error(())).sequence == Error(()))
    assert(List[A](Error(()), Ok(()), Error(())).sequence == Error(()))
  }

  test("returns lists if there are no errors") {
    assertEquals(List[A](Ok(()), Ok(())).sequence, Ok(List((), ())))
  }
