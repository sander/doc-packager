package nl.sanderdijkhuis.docpkg

import nl.sanderdijkhuis.docpkg.ContentManagement.{PageName, PagePath}
import nl.sanderdijkhuis.docpkg.LocalPageInventory.{InventoryError, Node, Page}

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

  test("inventory() of an empty directory is empty") {
    val directory = Node(Path.of("dir"), Nil, Nil)
    assertEquals(LocalPageInventory.inventory(directory).toList, Nil)
  }

  test("inventory() of a directory with one page") {
    val directory = Node(Path.of("dir"), Nil, List(Path.of("dir/foo.html")))
    assertEquals(
      LocalPageInventory.inventory(directory).toList,
      List(
        Page(PagePath.root, None, Nil),
        Page(
          PagePath(PageName.get("foo").get),
          Some(Path.of("dir/foo.html")),
          Nil
        )
      )
    )
  }

  test("inventory() of a directory with one main page") {
    val index = Path.of("dir/index.html")
    val directory = Node(Path.of("dir"), Nil, List(index))
    assertEquals(
      LocalPageInventory.inventory(directory).toList,
      List(Page(PagePath.root, Some(index), Nil))
    )
  }
