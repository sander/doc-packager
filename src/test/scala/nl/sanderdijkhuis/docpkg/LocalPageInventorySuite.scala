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

  private val dir = Path.of("dir")
  private val index = Path.of("dir/index.html")
  private val foo = Path.of("dir/foo.html")
  private val subdir = Path.of("subdir")
  private val bar = Path.of("subdir/bar.html")

  test("inventory() of an empty directory is empty") {
    val directory = Node(dir, Nil, Nil)
    assertEquals(LocalPageInventory.inventory(directory).toList, Nil)
  }

  test("inventory() of a directory with one page") {
    val directory = Node(dir, Nil, List(foo))
    assertEquals(
      LocalPageInventory.inventory(directory).toList,
      List(
        Page(PagePath.root, None, Nil),
        Page(PagePath(PageName.get("foo").get), Some(foo), Nil)
      )
    )
  }

  test("inventory() of a directory with one main page") {
    val directory = Node(dir, Nil, List(index))
    assertEquals(
      LocalPageInventory.inventory(directory).toList,
      List(Page(PagePath.root, Some(index), Nil))
    )
  }

  test("inventory() of a directory with an empty subdirectory") {
    val directory = Node(dir, List(Node(subdir, Nil, Nil)), Nil)
    assertEquals(LocalPageInventory.inventory(directory).toList, Nil)
  }

  test("inventory() of a directory with a subdirectory with one page") {
    val directory = Node(dir, List(Node(subdir, Nil, List(bar))), Nil)
    assertEquals(
      LocalPageInventory.inventory(directory).toList,
      List(
        Page(PagePath.root, None, Nil),
        Page(PagePath(PageName.get("subdir").get), None, Nil),
        Page(
          PagePath.appendTo(
            PagePath(PageName.get("subdir").get),
            PageName.get("bar").get
          ),
          Some(bar),
          Nil
        )
      )
    )
  }
