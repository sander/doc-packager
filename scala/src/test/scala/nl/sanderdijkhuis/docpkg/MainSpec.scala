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
        assert(
          result == ListMap(
            Nil -> List.empty,
            ("subfolder" :: Nil) -> List("extra.csv"),
            ("foo.html" :: "subfolder" :: Nil) -> List.empty,
            ("file2.html" :: Nil) -> List.empty
          )
        )
      }
    }
    it("test") {
      println(d.getPageContent(Main.Path(List.empty)))
    }
  }
