package docpkg

import docpkg.formatting.*

import scala.xml.*

class FormattingSuite extends munit.FunSuite:

  test("renders a directory to a list of its children") {
    val page = directoryPage()
    assertEquals(page.value.prefix, "ac")
    assertEquals(page.value.label, "structured-macro")
    assertEquals(page.value.attributes.collectFirst {
      case PrefixedAttribute("ac", "name", value, _) => value.text
    }, Some("children"))
  }
