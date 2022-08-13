package docpkg.content

import java.nio.file.Path
import scala.annotation.targetName

private val pageNameRegex = raw"^[a-zA-Z0-9-]+\z".r
private val attachmentNameRegex = raw"^[a-zA-Z0-9-.]+\z".r

opaque type PageName = String
object PageName:
  val empty: PageName = "-"
  def get(value: String): Option[PageName] = pageNameRegex.findFirstIn(value)
  def from(value: String): PageName =
    value.replaceAll(raw"[^a-zA-Z0-9-]", "-") match
      case "" => empty
      case s  => s
extension (n: PageName)
  def value: String = n
  @targetName("renamePage") def rename(i: Int): PageName =
    PageName.from(s"$n-$i")

opaque type PagePath = List[PageName]
object PagePath:
  def apply(name: PageName): PagePath = List(name)
  def from(names: List[PageName]): Option[PagePath] = Some(names)
  def root: PagePath = List.empty
  def appendTo(path: PagePath, name: PageName): PagePath = name :: path
extension (p: PagePath)
  def toStringPath: String = "/" + p.reverse.mkString("/")
  def hasAncestor(q: PagePath): Boolean = p match
    case t if t == q => true
    case _ :: t      => t.hasAncestor(q)
    case Nil         => false

opaque type AttachmentName = String
object AttachmentName:
  val empty: PageName = "attachment.bin"
  def get(value: String): Option[AttachmentName] =
    attachmentNameRegex.findFirstIn(value)
  def from(value: String): AttachmentName =
    value.replaceAll(raw"[^a-zA-Z0-9-.]", "-") match
      case "" => empty
      case s  => s
extension (n: AttachmentName)
  @targetName("attachmentNameToString") def toString: String = n
  @targetName("renameAttachment") def rename(i: Int): AttachmentName =
    val withExtension = raw"\A(.+)\.(.+)\z".r
    AttachmentName
      .from(n.toString match
        case withExtension(name, extension) => s"$name-$i.$extension"
        case s                              => s"$s-$i"
      )

opaque type Id = String
object Id:
  def parse(value: String): Option[Id] = Some(value)

opaque type Title = String
extension (t: Title) def toString: String = t
object PageTitle:
  def parse(value: String): Option[Title] = Some(value)

opaque type Body = String
extension (b: Body) @targetName("bodyValue") def value: String = b
object PageBody:
  def parse(value: String): Option[Body] = Some(value)

opaque type Version = Int
extension (v: Version)
  def increment: Version = v + 1
  def value: Int = v
def Version(value: Int): Option[Version] = Some(value)

case class Attachment(name: AttachmentName, file: Path)
