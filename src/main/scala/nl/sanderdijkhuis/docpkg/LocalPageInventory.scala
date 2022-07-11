package nl.sanderdijkhuis.docpkg

import nl.sanderdijkhuis.docpkg.ContentManagement.{AttachmentName, PagePath}

import java.io.File
import java.nio.file.Path
import scala.collection.immutable.ListMap

object LocalPageInventory:
  case class Attachment(name: AttachmentName, file: File)
  case class Page(path: PagePath, file: File, attachments: List[Attachment])
  opaque type BreadthFirstTraversal = List[Page]

  enum Error:
    case NotADirectory

  def apply(path: Path): Either[Error, BreadthFirstTraversal] = ???
