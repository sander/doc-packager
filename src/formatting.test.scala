package docpkg

import docpkg.formatting.*

import scala.xml.*

class FormattingSuite extends munit.FunSuite:

  def attribute(elem: Elem, prefix: String, name: String): Option[String] =
    elem.attributes.collectFirst {
      case PrefixedAttribute(prefix, name, value, _) => value.text
    }

  test("renders a directory as a list of its children") {
    val p = Page.index
    assertEquals(p.contents.length, 1)
    assertEquals(p.contents.head.prefix, "ac")
    assertEquals(p.contents.head.label, "structured-macro")
    assertEquals(attribute(p.contents.head, "ac", "name"), Some("children"))
  }

  test("renders a file initially as plain text") {
    val f = File.from("<p>test</p>").get
    val p = Page(f)
    assert(p.contents.head.toString.contains("&lt;p&gt;test&lt;/p&gt;"))
  }
