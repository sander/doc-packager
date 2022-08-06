package nl.sanderdijkhuis.docpkg

import io.cucumber.datatable.DataTable
import io.cucumber.scala.{EN, PendingException, ScalaDsl, Scenario}
import io.cucumber.scala.Implicits.*
import nl.sanderdijkhuis.docpkg.LocalPageInventory.{
  BreadthFirstTraversal,
  InventoryError
}
import munit.Assertions.*

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator

import scala.collection.immutable.ListMap

class StepDefinitions extends ScalaDsl with EN:
  val directory: Path =
    Files.createTempDirectory(Paths.get("target"), getClass.getName)
  var success: Boolean = false
  var traversal: Option[BreadthFirstTraversal] = None
  var error: Option[InventoryError] = None

  given munit.Location = munit.Location.empty // Enable MUnit assertions

  Before {
    directory.toFile.mkdirs()
    ()
  }

  After {
    Files
      .walk(directory)
      .sorted(Comparator.reverseOrder())
      .map(_.toFile)
      .forEach(_.delete)
  }

  When("""I launch the application""") { () =>
    if (DocumentationPackager.run() == ()) success = true
  }
  Then("""it exits successfully""") { () =>
    assert(success)
  }
  Given("""a directory with contents""") { (dataTable: DataTable) =>
    for f <- dataTable.asScalaMaps
        .map(_("File path").get)
        .map(File(directory.toFile, _))
    yield
      f.getParentFile.mkdirs()
      assert(f.createNewFile())
  }
  When("""I generate a local inventory""") { () =>
    val result = LocalPageInventory(directory)
    assertEquals(result.error, None)
    traversal = result.toOption
  }
  Then("""the inventory contains the following pages:""") {
    (dataTable: DataTable) =>
      val x =
        ListMap.from(
          for f <- dataTable.asScalaMaps.toList
          yield f("Inventory path").get -> f("File path")
        )
      val y = ListMap.from(
        traversal.get.toList.map(p =>
          p.path.toStringPath -> p.content.map(f =>
            s"${f.toString.drop(directory.toString.length)}"
          )
        )
      )
      assertEquals(y, x)
  }
  Then("""the inventory contains the following attachments:""") {
    (dataTable: DataTable) =>
      throw PendingException()
  }
  Given("I ask to generate a local inventory for a file") { () =>
    val file = Files.createTempFile(directory, "file", "txt")
    error = LocalPageInventory(file).error
  }
  Then("I get an error that the provided path is not a directory") { () =>
    assert(error.contains(InventoryError.NotADirectory))
  }
