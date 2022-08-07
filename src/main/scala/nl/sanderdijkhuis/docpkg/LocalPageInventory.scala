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

  private val pageSuffix = ".html"
  private val mainPageName = s"index$pageSuffix"
  private val initialCounter = 1

  private case class UniquenessState[T, N](
      counters: Map[N, Int] = Map.empty[N, Int],
      out: List[T] = List.empty
  )
  private def ensureUniqueNames[T, N](
      in: List[T],
      name: T => N,
      rename: (N, Int) => N,
      changeName: (T, N) => T,
      initialState: UniquenessState[T, N] = UniquenessState[T, N]()
  ): UniquenessState[T, N] =
    val names = in.map(name)
    def operator(state: UniquenessState[T, N], x: T): UniquenessState[T, N] =
      state.counters.get(name(x)) match
        case Some(i) =>
          Iterator
            .from(i)
            .map(j => j -> rename(name(x), j + 1))
            .find { case (_, n) =>
              !names.contains(n)
            }
            .map { case (j, n) =>
              UniquenessState(
                state.counters + (name(x) -> (j + 1)),
                changeName(x, n) :: state.out
              )
            }
            .get
        case None =>
          UniquenessState(
            state.counters + (name(x) -> initialCounter),
            x :: state.out
          )
    val UniquenessState(counters, out) = in.foldLeft(initialState)(operator)
    UniquenessState(counters, out.reverse)

  private def ensureUniqueAttachmentNames(
      in: List[Attachment]
  ): List[Attachment] =
    ensureUniqueNames[Attachment, AttachmentName](
      in,
      _.name,
      (n, i) => n.rename(i),
      (a, n) => a.copy(name = n)
    ).out

  def inventory(
      node: Node,
      path: PagePath = PagePath.root
  ): BreadthFirstTraversal =
    case class State[T](
        counters: Map[PageName, Int] = Map.empty,
        out: List[T] = List.empty
    )
    def name(s: String): PageName = PageName.from(s)
    def rename(name: PageName, i: Int): PageName =
      PageName.from(s"${name.toString}-$i")
    extension (p: PagePath)
      @targetName("appendTo")
      def +(n: PageName): PagePath = PagePath.appendTo(p, n)
    val UniquenessState(counters, directories) =
      ensureUniqueNames[(PageName, Node), PageName](
        node.directories.map(n => name(n.path.getFileName.toString) -> n),
        _._1,
        (n, i) => n.rename(i),
        (n, name) => name -> n._2
      )
    (
      directories.flatMap { case (name, node) => inventory(node, path + name) },
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
        val root = Page(
          path,
          main,
          ensureUniqueAttachmentNames(directAttachments ++ deepAttachments)
        )
        val pin = for
          p <- files
          f = p.getFileName.toString
          if f != mainPageName && f.endsWith(pageSuffix)
          n = name(p.getFileName.toString.dropRight(pageSuffix.length))
        yield n -> p
        val pages = pin
          .foldLeft(State[Page](counters)) { case (s, (n, p)) =>
            s.counters.get(n) match
              case Some(i) =>
                Iterator
                  .from(i)
                  .map(j => j -> rename(n, j + 1))
                  .find { case (_, n) =>
                    pin.forall(_._1 != n)
                  }
                  .map { case (j, m) =>
                    State(
                      s.counters + (n -> (j + 1)),
                      Page(path + m, Some(p), Nil) :: s.out
                    )
                  }
                  .get
              case None =>
                State(
                  s.counters + (n -> initialCounter),
                  Page(path + n, Some(p), Nil) :: s.out
                )
          }
          .out
          .reverse
        root :: directoriesWithContent ++ pages

  def apply(path: Path): Outcome =
    if !path.toFile.isDirectory then Error(InventoryError.NotADirectory)
    else ???
