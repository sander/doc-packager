package nl.sanderdijkhuis.docpkg

import nl.sanderdijkhuis.docpkg.LocalPageInventory.InventoryError

import java.nio.file.{Files, Path}

class LocalPageInventorySuite extends munit.FunSuite:

  val temporaryFile: FunFixture[Path] =
    FunFixture[Path](
      test => Files.createTempFile("tmp", test.name),
      Files.deleteIfExists
    )

  temporaryFile.test("traverse() does not accept files") { path =>
    assertEquals(LocalPageInventory.traverse(path), None)
  }
