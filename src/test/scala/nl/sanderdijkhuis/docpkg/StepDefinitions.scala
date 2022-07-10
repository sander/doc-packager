package nl.sanderdijkhuis.docpkg

import io.cucumber.datatable.DataTable
import io.cucumber.scala.{EN, PendingException, ScalaDsl, Scenario}
import io.cucumber.scala.Implicits.*

import java.io.File
import java.nio.file.{Files, Path, Paths}
import java.util.Comparator

class StepDefinitions extends ScalaDsl with EN:
  val directory: Path =
    Files.createTempDirectory(Paths.get("target"), getClass.getName)
  var success: Boolean = false

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
    throw PendingException()
  }
  Then("""the inventory contains the following pages:""") {
    (dataTable: DataTable) =>
      throw PendingException()
  }
  Then("""the inventory contains the following attachments:""") {
    (dataTable: DataTable) =>
      throw PendingException()
  }
