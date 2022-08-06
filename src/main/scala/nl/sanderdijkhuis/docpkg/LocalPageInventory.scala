package nl.sanderdijkhuis.docpkg

import nl.sanderdijkhuis.docpkg.ContentManagement.{
  AttachmentName,
  PageName,
  PagePath
}
import nl.sanderdijkhuis.docpkg.Traversal.{Error, Result}

import java.io.{File, IOException}
import java.nio.file.{Files, NotDirectoryException, Path}
import scala.util.{Failure, Success, Try, Using}
import scala.jdk.StreamConverters.*

object LocalPageInventory:
  case class Attachment(name: AttachmentName, file: Path)
  case class Page(
      path: PagePath,
      content: Option[Path],
      attachments: List[Attachment]
  )

  opaque type BreadthFirstTraversal = List[Page]
  extension (t: BreadthFirstTraversal) def toList: List[Page] = t

  sealed trait InventoryError
  object InventoryError:
    case object NotADirectory extends InventoryError
    case class InvalidAttachmentNameError(path: Path) extends InventoryError
    case class InvalidPageNameError(path: Path) extends InventoryError

  type Outcome = Result[InventoryError, BreadthFirstTraversal]

  case class Node(path: Path, directories: List[Node], files: List[Path])

  opaque type Depth = Int
  object Depth:
    val maximum: Depth = 5
  extension (d: Depth)
    def decrement: Option[Depth] = if d == 0 then None else Some(d - 1)

  case class MaximumDepthReached(path: Path)
      extends Throwable(
        s"Maximum traversal depth reached while traversing $path"
      )

  type TraversalError = NotDirectoryException | SecurityException |
    IOException | MaximumDepthReached

  def traverseDepthFirst(
      path: Path,
      depth: Depth = Depth.maximum
  ): Either[TraversalError, Node] =
    val result = Using(Files.list(path))(_.toScala(List)).flatMap(paths =>
      Try(paths.partition(Files.isDirectory(_)))
    )
    (result, depth.decrement) match
      case (Success((directories, files)), Some(d)) =>
        directories
          .map(traverseDepthFirst(_, d))
          .partitionMap(identity) match
          case (Nil, nodes) => Right(Node(path, nodes, files))
          case (e :: _, _)  => Left(e)
      case (_, None)                       => Left(MaximumDepthReached(path))
      case (Failure(e: TraversalError), _) => Left(e)
      case (Failure(e), _)                 => throw e

  val pageSuffix = ".html"
  val mainPageName = s"index$pageSuffix"

  def inventory(node: Node): BreadthFirstTraversal =
    // TODO ensure all PagePaths are unique; could add numbers
    (node.directories.flatMap(inventory), node.files) match
      case (Nil, Nil) => Nil
      case _ =>
        val path = PagePath.root
        val main = node.files.find(_.getFileName.toString == mainPageName)
        val root = Page(path, main, Nil)
        val pages = for
          p <- node.files
          f = p.getFileName.toString
          if f != mainPageName && f.endsWith(pageSuffix)
          n = PageName.from(p.getFileName.toString.dropRight(pageSuffix.length))
        yield Page(PagePath.appendTo(path, n), Some(p), Nil)
        root :: pages

  def apply(path: Path): Outcome =
    if !path.toFile.isDirectory then Error(InventoryError.NotADirectory)
    else ???
