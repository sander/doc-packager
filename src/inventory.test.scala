package docpkg

import docpkg.content.*
import docpkg.inventory.*

import java.nio.file.{Files, NotDirectoryException, Path}

class LocalPageInventorySuite extends munit.FunSuite:

  val temporaryFile: FunFixture[Path] =
    FunFixture[Path](
      test => Files.createTempFile("tmp", test.name),
      Files.deleteIfExists
    )

  temporaryFile.test("reject files instead of directories") { path =>
    val result = traverseDepthFirst(path)
    intercept[NotDirectoryException](result.toTry.get)
  }

  private val dir = Path.of("dir")
  private val index = Path.of("dir/index.html")
  private val foo = Path.of("dir/foo.html")
  private val subdir = Path.of("dir/subdir")
  private val bar = Path.of("dir/subdir/bar.html")
  private val pic = Path.of("dir/picture.png")
  private val subpic = Path.of("dir/subdir/picture.png")
  private val pageWithSpecialCharacter1 = Path.of("dir/fóó.html")
  private val pageWithSpecialCharacter2 = Path.of("dir/fòò.html")
  private val subdirWithSpecialCharacter1 = Path.of("dir/föö")
  private val subdirWithSpecialCharacter2 = Path.of("dir/fåå")
  private val pageInSubdirWithSpecialCharacter1 = Path.of("dir/föö/bar.html")
  private val pageInSubdirWithSpecialCharacter2 = Path.of("dir/fåå/bar.html")
  private val subsubdir = Path.of("dir/subdir/subsubdir")
  private val subsubdirPage = Path.of("dir/subdir/subsubdir/page.html")

  test("consider an empty directory to be empty") {
    val directory = Node(dir, Nil, Nil)
    assertEquals(inventory(directory).toList, Nil)
  }

  test("inventory a directory with one page") {
    val directory = Node(dir, Nil, List(foo))
    assertEquals(
      inventory(directory).toList,
      List(
        Page(PagePath.root, None, Nil),
        Page(PagePath(PageName.get("foo").get), Some(foo), Nil)
      )
    )
  }

  test("inventory a directory with one main page") {
    val directory = Node(dir, Nil, List(index))
    assertEquals(
      inventory(directory).toList,
      List(Page(PagePath.root, Some(index), Nil))
    )
  }

  test("inventory a directory with an empty subdirectory") {
    val directory = Node(dir, List(Node(subdir, Nil, Nil)), Nil)
    assertEquals(inventory(directory).toList, Nil)
  }

  test("inventory a directory with a subdirectory with one page") {
    val directory = Node(dir, List(Node(subdir, Nil, List(bar))), Nil)
    def name(s: String) = PageName.get(s).get
    def path(s: String) = PagePath(name(s))
    assertEquals(
      inventory(directory).toList,
      List(
        Page(PagePath.root, None, Nil),
        Page(path("subdir"), None, Nil),
        Page(PagePath.appendTo(path("subdir"), name("bar")), Some(bar), Nil)
      )
    )
  }

  test("inventory a directory with an attachment") {
    val directory = Node(dir, Nil, List(pic))
    val attachment = Attachment(AttachmentName.get("picture.png").get, pic)
    assertEquals(
      inventory(directory).toList,
      List(Page(PagePath.root, None, List(attachment)))
    )
  }

  test("inventory a directory with a subdirectory with an attachment") {
    val directory = Node(dir, List(Node(subdir, Nil, List(subpic))), Nil)
    val attachment =
      Attachment(AttachmentName.get("picture.png").get, subpic)
    assertEquals(
      inventory(directory).toList,
      List(Page(PagePath.root, None, List(attachment)))
    )
  }

  test("inventory with duplicate attachment names") {
    val directory = Node(dir, List(Node(subdir, Nil, List(subpic))), List(pic))
    val attachment1 = Attachment(AttachmentName.get("picture.png").get, pic)
    val attachment2 =
      Attachment(AttachmentName.get("picture-2.png").get, subpic)
    assertEquals(
      inventory(directory).toList,
      List(Page(PagePath.root, None, List(attachment1, attachment2)))
    )
  }

  test("inventory with duplicate page names") {
    val directory =
      Node(dir, Nil, List(pageWithSpecialCharacter1, pageWithSpecialCharacter2))
    assertEquals(
      inventory(directory).toList,
      List(
        Page(PagePath.root, None, Nil),
        Page(
          PagePath(PageName.get("f--").get),
          Some(pageWithSpecialCharacter1),
          Nil
        ),
        Page(
          PagePath(PageName.get("f---2").get),
          Some(pageWithSpecialCharacter2),
          Nil
        )
      )
    )
  }

  test("inventory with duplicate page and directory names") {
    val directory =
      Node(
        dir,
        List(
          Node(
            subdirWithSpecialCharacter1,
            Nil,
            List(pageInSubdirWithSpecialCharacter1)
          ),
          Node(
            subdirWithSpecialCharacter2,
            Nil,
            List(pageInSubdirWithSpecialCharacter2)
          )
        ),
        List(pageWithSpecialCharacter1, pageWithSpecialCharacter2)
      )
    assertEquals(
      inventory(directory).toList,
      List(
        Page(PagePath.root, None, Nil),
        Page(
          PagePath(PageName.get("f--").get),
          None,
          Nil
        ),
        Page(
          PagePath
            .appendTo(
              PagePath(PageName.get("f--").get),
              PageName.get("bar").get
            ),
          Some(pageInSubdirWithSpecialCharacter1),
          Nil
        ),
        Page(
          PagePath(PageName.get("f---2").get),
          None,
          Nil
        ),
        Page(
          PagePath
            .appendTo(
              PagePath(PageName.get("f---2").get),
              PageName.get("bar").get
            ),
          Some(pageInSubdirWithSpecialCharacter2),
          Nil
        ),
        Page(
          PagePath(PageName.get("f---3").get),
          Some(pageWithSpecialCharacter1),
          Nil
        ),
        Page(
          PagePath(PageName.get("f---4").get),
          Some(pageWithSpecialCharacter2),
          Nil
        )
      )
    )
  }

  test("inventory with page in sub-sub-directory") {
    val directory = Node(
      dir,
      List(Node(subdir, List(Node(subsubdir, Nil, List(subsubdirPage))), Nil)),
      Nil
    )
    val subdirPath = PagePath(PageName.get("subdir").get)
    val subsubdirPath =
      PagePath.appendTo(subdirPath, PageName.get("subsubdir").get)
    val pagePath = PagePath.appendTo(subsubdirPath, PageName.get("page").get)
    assertEquals(
      inventory(directory).toList,
      List(
        Page(PagePath.root, None, Nil),
        Page(subdirPath, None, Nil),
        Page(subsubdirPath, None, Nil),
        Page(pagePath, Some(subsubdirPage), Nil)
      )
    )
  }
