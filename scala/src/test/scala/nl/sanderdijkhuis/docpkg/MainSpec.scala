package nl.sanderdijkhuis.docpkg

import org.scalatest.funspec.AnyFunSpec

import scala.collection.immutable.ListMap
import scala.io.Source

class MainSpec extends AnyFunSpec:
  describe("LocalDirectory") {
    val name = getClass.getResource("/site").toURI
    val d = Main.LocalDirectory(name) match {
      case Left(e)  => throw new Exception(e)
      case Right(d) => d
    }
    describe("traverse") {
      it("traverses") {
        val result = d.traverse()
        println(result)
        assert(result.toList.length == 4)
        assert(
          result == ListMap(
            List.empty -> List.empty,
            List("subfolder") -> List("extra.csv"),
            ("foo.html" :: "subfolder" :: List.empty) -> List.empty,
            List("file2.html") -> List.empty
          )
        )
      }
    }
  }
