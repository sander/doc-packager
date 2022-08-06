package nl.sanderdijkhuis.docpkg

import nl.sanderdijkhuis.docpkg.LocalPageInventory.InventoryError

import java.nio.file.{Files, NotDirectoryException, Path}

class LocalPageInventorySuite extends munit.FunSuite:

  val temporaryFile: FunFixture[Path] =
    FunFixture[Path](
      test => Files.createTempFile("tmp", test.name),
      Files.deleteIfExists
    )

  temporaryFile.test("traverseDepthFirst() does not accept files") { path =>
    val result = LocalPageInventory.traverseDepthFirst(path)
    intercept[NotDirectoryException](result.toTry.get)
  }
