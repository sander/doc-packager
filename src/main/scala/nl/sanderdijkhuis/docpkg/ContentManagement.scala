package nl.sanderdijkhuis.docpkg

object ContentManagement:

  private val nameRegex = raw"^[a-zA-Z0-9-]+\z".r

  opaque type PageName = String
  object PageName:
    def get(value: String): Option[PageName] = nameRegex.findFirstIn(value)

  opaque type PagePath = List[PageName]
  object PagePath:
    def apply(name: PageName): PagePath = List(name)

  opaque type AttachmentName = String
  object AttachmentName:
    def get(value: String): Option[AttachmentName] =
      nameRegex.findFirstIn(value)
