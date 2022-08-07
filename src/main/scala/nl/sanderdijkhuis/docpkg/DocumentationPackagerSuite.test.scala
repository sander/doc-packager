//> using lib "org.scalameta::munit::1.0.0-M6"

package nl.sanderdijkhuis.docpkg

class DocumentationPackagerSuite extends munit.FunSuite:
  test("run() returns") {
    assertEquals(DocumentationPackager.run(), ())
  }
