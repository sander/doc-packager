package nl.sanderdijkhuis.docpkg

class DocumentationPackagerSuite extends munit.FunSuite {
  test("run() returns") {
    assertEquals(DocumentationPackager.run(), ())
  }
}
