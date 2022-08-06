package nl.sanderdijkhuis.docpkg

import nl.sanderdijkhuis.docpkg.ContentManagement.{AttachmentName, PagePath}
import nl.sanderdijkhuis.docpkg.Traversal.{Error, Result}

import java.io.File
import java.nio.file.{Files, Path}

object LocalPageInventory:
  case class Attachment(name: AttachmentName, file: File)
  case class Page(
      path: PagePath,
      content: Option[File],
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

  enum TraversalNode:
    case Directory(name: String, children: List[TraversalNode])
    case File(name: String)

  def traverse(path: Path): Option[TraversalNode] =
    Some(path).filter(Files.isDirectory(_)).map(_ => ???)

  def apply(path: Path): Outcome =
    if !path.toFile.isDirectory then Error(InventoryError.NotADirectory)
    else ???
