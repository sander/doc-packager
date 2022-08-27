package docpkg

import docpkg.formatting.*

import scala.xml.*

class FormattingSuite extends munit.FunSuite:

  def attribute(elem: Elem, prefix: String, name: String): Option[String] =
    elem.attributes.collectFirst {
      case PrefixedAttribute(prefix, name, value, _) => value.text
    }

  test("renders a directory to a list of its children") {
    val page = directoryPage()
    assertEquals(page.value.prefix, "ac")
    assertEquals(page.value.label, "structured-macro")
    assertEquals(attribute(page.value, "ac", "name"), Some("children"))
  }
