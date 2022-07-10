package nl.sanderdijkhuis.docpkg

import io.cucumber.datatable.DataTable
import io.cucumber.scala.{EN, PendingException, ScalaDsl}

class StepDefinitions extends ScalaDsl with EN:
  var success: Boolean = false

  When("""I launch the application""") { () =>
    if (DocumentationPackager.run() == ()) success = true
  }
  Then("""it exits successfully""") { () =>
    assert(success)
  }
  Given("""a directory with contents""") { (dataTable: DataTable) =>
    throw PendingException()
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
