package nl.sanderdijkhuis.docpkg

import nl.sanderdijkhuis.docpkg.ContentManagement.{
  AttachmentName,
  PageName,
  PagePath
}
import nl.sanderdijkhuis.docpkg.LocalPageInventory.Page
import nl.sanderdijkhuis.docpkg.Traversal.{Error, Result}

import java.io.{File, IOException}
import java.nio.file.{Files, NotDirectoryException, Path}
import scala.annotation.targetName
import scala.collection.immutable.ListMap
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
    val result =
      Using(Files.list(path))(_.toScala(List).sorted).flatMap(paths =>
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

  private val pageSuffix = ".html"
  private val mainPageName = s"index$pageSuffix"
  private val initialCounter = 1

  private case class UniquenessState[N, T](
      counters: Map[N, Int] = Map.empty[N, Int],
      out: List[(N, T)] = List.empty
  )
  private def ensureUniqueNames[N, T](
      in: List[(N, T)],
      rename: (N, Int) => N,
      initialState: UniquenessState[N, T] = UniquenessState[N, T]()
  ): UniquenessState[N, T] =
    type S = UniquenessState[N, T]
    val names = in.map(_._1).toSet
    def operator(state: S, x: (N, T)): S =
      val (name, value) = x
      state.counters.get(name) match
        case Some(i) =>
          Iterator
            .from(i)
            .map(j => j -> rename(name, j + 1))
            .find((_, n) => !names.contains(n))
            .map((j, n) =>
              state.copy(
                counters = state.counters + (name -> (j + 1)),
                out = (n -> value) :: state.out
              )
            )
            .get
        case None =>
          state.copy(
            counters = state.counters + (name -> initialCounter),
            out = (name -> value) :: state.out
          )
    val S(counters, out) = in.foldLeft(initialState)(operator)
    UniquenessState(counters, out.reverse)

  def inventory(
      node: Node,
      path: PagePath = PagePath.root
  ): BreadthFirstTraversal =
    val UniquenessState(counters, directories) =
      ensureUniqueNames(
        node.directories.map(n =>
          PageName.from(n.path.getFileName.toString) -> n
        ),
        (n, i) => n.rename(i)
      )
    (
      directories.flatMap { case (name, node) =>
        inventory(node, PagePath.appendTo(path, name))
      },
      node.files
    ) match
      case (Nil, Nil) => Nil
      case (directories, files) =>
        val (directoriesWithContent, directoriesWithoutContent) =
          directories.partition(page =>
            directories.exists(d =>
              d.path.startsWith(page.path) && d.content.nonEmpty
            )
          )
        val deepAttachments = directoriesWithoutContent.flatMap(_.attachments)
        val main = files.find(_.getFileName.toString == mainPageName)
        val directAttachments = for
          p <- files
          f = p.getFileName.toString
          if !f.endsWith(pageSuffix)
        yield Attachment(AttachmentName.from(f), p)
        val attachments = ensureUniqueNames(
          (directAttachments ++ deepAttachments).map(a => a.name -> a),
          (n, i) => n.rename(i)
        ).out.map { case (n, a) => a.copy(name = n) }
        val root = Page(
          path,
          main,
          attachments
        )
        val pin = for
          p <- files
          f = p.getFileName.toString
          if f != mainPageName && f.endsWith(pageSuffix)
          n = PageName.from(p.getFileName.toString.dropRight(pageSuffix.length))
        yield n -> p
        val pages = ensureUniqueNames(
          pin,
          (n, i) => n.rename(i),
          UniquenessState(counters)
        ).out.map { case (name, p) =>
          Page(PagePath.appendTo(path, name), Some(p), Nil)
        }
        root :: directoriesWithContent ++ pages
