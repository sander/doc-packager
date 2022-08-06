package nl.sanderdijkhuis.docpkg

import java.nio.file.Path

object ContentManagement:

  private val pageNameRegex = raw"^[a-zA-Z0-9-]+\z".r
  private val attachmentNameRegex = raw"^[a-zA-Z0-9-.]+\z".r

  opaque type PageName = String
  object PageName:
    def get(value: String): Option[PageName] = pageNameRegex.findFirstIn(value)
  extension (n: PageName) def value: String = n

  opaque type PagePath = List[PageName]
  object PagePath:
    def apply(name: PageName): PagePath = List(name)
    def root: PagePath = List.empty
    def appendTo(path: PagePath, name: PageName): PagePath = name :: path
  extension (p: PagePath)
    def toStringPath: String = "/" + p.reverse.mkString("/")

  opaque type AttachmentName = String
  object AttachmentName:
    def get(value: String): Option[AttachmentName] =
      attachmentNameRegex.findFirstIn(value)
