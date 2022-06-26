package nl.sanderdijkhuis.docpkg

import sttp.client3.*
import sttp.client3.circe.*
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.monad.syntax.*
import io.circe.generic.auto.*
import io.circe.*
import io.circe.syntax.*
import nl.sanderdijkhuis.docpkg.Confluence.Space.Property.Key
import nl.sanderdijkhuis.docpkg.Main.Content.{Id, Version}

import java.io.File
import java.net.URI
import java.util.{Base64, UUID}
import scala.collection.immutable.ListMap

object Main:

  opaque type Name = String
  opaque type Path = List[Name]
  opaque type Attachment = File

  case class Content(title: Content.Title, body: Content.Body)

  object Content:
    opaque type Id = String
    opaque type Title = String
    opaque type Body = String
    opaque type Version = Int

    extension (v: Version) def increment: Version = v + 1

  trait LocalDirectory:
    def traverse(): LocalDirectory.BreadthFirstTraversal
    def getPageContent(path: Path): Content
    def getAttachment(path: Path, name: Name): Attachment

  object LocalDirectory:
    type BreadthFirstTraversal = ListMap[Path, List[Name]]

    extension (l: BreadthFirstTraversal)
      def hasAttachment(path: Path, name: Name): Boolean =
        l.get(path).map(_.collectFirst(_ == name)).nonEmpty

    def apply(path: URI): Either[String, LocalDirectory] =
      val d = File(path)
      if (!d.isDirectory) Left("No directory")
      else
        Right(
          new LocalDirectory {
            override def traverse(): BreadthFirstTraversal =
              ListMap.from[Path, List[Name]](
                for f <- d.listFiles().toList
                yield (List[Name](f.getName) -> List.empty[Name])
              )

            override def getPageContent(path: Path): Content = ???

            override def getAttachment(path: Path, name: Name): Attachment = ???
          }
        )

  trait RemoteSpace:
    def acquireMutex(): Unit
    def releaseMutex(): Unit
    def list(): RemoteSpace.Inventory
    def create(path: Path, content: Content): Content.Id
    def upload(
        contentId: Content.Id,
        name: Name,
        attachment: Attachment
    ): Unit
    def deletePage(contentId: Content.Id): Unit
    def deleteAttachment(contentId: Content.Id, name: Name): Unit
    def getUpdatablePageContent(
        contentId: Content.Id
    ): RemoteSpace.UpdatablePageContent
    def updatePageContent(
        contentId: Content.Id,
        version: Content.Version,
        content: Content
    ): Unit
    def appendTo(
        pageId: Content.Id,
        targetId: Content.Id
    ): Unit // PUT /wiki/rest/api/content/{pageId}/move/{position}/{targetId}

  object RemoteSpace:
    private val prefix = "docpkg"

    case class RemoteContent(id: Content.Id, attachments: List[Name])

    type Inventory = Map[Path, RemoteContent]

    extension (r: Inventory)
      def hasAttachment(path: Path, name: Name): Boolean =
        r.get(path).map(_.attachments.collectFirst(_ == name)).nonEmpty

    case class UpdatablePageContent(
        version: Content.Version,
        content: Content
    )

//    case class Live() extends RemoteSpace

    def apply(
        domainName: String,
        userName: String,
        spaceId: String
    ): Confluence.Token ?=> SttpBackend[Identity, Any] ?=> Either[
      String,
      RemoteSpace
    ] =
      val backend = summon[SttpBackend[Identity, Any]]
      Confluence.Space.Key.parse(spaceId) match {
        case None => Left("Could not parse key")
        case Some(sk) =>
          Right(new RemoteSpace {

            val mutexKey: Confluence.Space.Property.Key =
              Confluence.Space.Property.Key
                .parse(s"$prefix.mutex")
                .getOrElse(throw new Exception("Could not parse key"))

            override def acquireMutex(): Unit =
              Confluence.Space
                .getSpaceProperty(sk, mutexKey)
                .send(backend)
                .body
                .getOrElse(throw new Exception("No body")) match {
                case Some(s) =>
                  throw new Exception("Mutex is already acquired")
                case None =>
                  val id = UUID.randomUUID().toString
                  Confluence.Space
                    .updateSpaceProperty(sk, mutexKey, id)
                    .send(backend)
                  Confluence.Space
                    .getSpaceProperty(sk, mutexKey)
                    .send(backend)
                    .body
                    .getOrElse(throw new Exception("No body")) match {
                    case Some(s) if s == id => ()
                    case Some(s) if s != id =>
                      throw new Exception("Race condition")
                    case None => throw new Exception("Failed to acquire mutex")
                  }
              }

            override def releaseMutex(): Unit = Confluence.Space
              .deleteSpaceProperty(sk, mutexKey)
              .send(backend)
              .body
              .getOrElse(throw new Exception("Could not release mutex"))

            override def list(): Inventory = Map.empty

            override def create(path: Path, content: Content): Id = ???

            override def upload(
                contentId: Id,
                name: Name,
                attachment: Attachment
            ): Unit = ???

            override def deletePage(contentId: Id): Unit = ???

            override def deleteAttachment(contentId: Id, name: Name): Unit = ???

            override def getUpdatablePageContent(
                contentId: Id
            ): UpdatablePageContent = ???

            override def updatePageContent(
                contentId: Id,
                version: Version,
                content: Content
            ): Unit = ???

            override def appendTo(pageId: Id, targetId: Id): Unit = ???
          })
      }

  type LocalReader[T] = LocalDirectory ?=> T
  type RemoteReaderWriter[T] = RemoteSpace ?=> T

  case class SyncResult(
      pagesCreated: Int,
      pagesUpdated: Int,
      pagesDeleted: Int,
      pagesRepositioned: Int,
      attachmentsCreated: Int,
      attachmentsUpdated: Int,
      attachmentsDeleted: Int
  )

  def resetMutex(): RemoteReaderWriter[Unit] =
    summon[RemoteSpace].releaseMutex()

  def syncProcess(): RemoteReaderWriter[LocalReader[SyncResult]] =
    import LocalDirectory._
    import Content._

    val local: LocalDirectory = summon[LocalDirectory]
    val remote = summon[RemoteSpace]

    remote.acquireMutex()

    val source = local.traverse()
    val target = remote.list()

    val createdPages = (for p <- source.keys if !target.contains(p) yield
      val content = local.getPageContent(p)
      val id = remote.create(p, content)
      p -> id
    ).toMap
    val ids: Map[Path, Content.Id] =
      target.map((p, c) => p -> c.id) ++ createdPages
    val createdAttachments = for
      (p, ns) <- source.toList
      n <- ns if !target.hasAttachment(p, n)
    yield
      val a = local.getAttachment(p, n)
      remote.upload(ids(p), n, a)
      (p, n)
    val deletedAttachments = for
      (p, c) <- target.toList
      n <- c.attachments if !source.hasAttachment(p, n)
    yield
      remote.deleteAttachment(target(p).id, n)
      (p, n)
    val deletedPages = for
      (p, RemoteSpace.RemoteContent(id, _)) <- target.toList
      if !source.contains(p)
    yield
      remote.deletePage(id)
      p
    val updatedPages = for
      p <- source.keys.toList if target.contains(p)
      localContent = local.getPageContent(p)
      updatableContent = remote.getUpdatablePageContent(target(p).id)
      if localContent != updatableContent.content
    yield
      remote.updatePageContent(
        target(p).id,
        updatableContent.version.increment,
        localContent
      )
      p
    val updatedAttachments = for
      (p, ns) <- source.toList
      n <- ns if target.hasAttachment(p, n)
    yield
      val a = local.getAttachment(p, n)
      remote.upload(target(p).id, n, a)
      (p, n)
    val repositionedPages = for p <- source.keys.toList if p.nonEmpty
    yield
      remote.appendTo(ids(p), ids(p.tail))
      p

    remote.releaseMutex()

    SyncResult(
      pagesCreated = createdPages.toList.length,
      pagesUpdated = updatedPages.length,
      pagesDeleted = deletedPages.length,
      pagesRepositioned = repositionedPages.length,
      attachmentsCreated = createdAttachments.length,
      attachmentsUpdated = updatedAttachments.length,
      attachmentsDeleted = deletedAttachments.length
    )

/** https://sanderd.atlassian.net/wiki/spaces/SANDER/overview?homepageId=33205
  */
object Configuration:
  val userName = "mail@sanderdijkhuis.nl"
  val domainName = "sanderd.atlassian.net"
  val spaceId = "SANDER"
  // val contentId = "1998849" // Configuration
  // val contentId = "33080" // Voorbeeldpagina's
//  val contentId = Content.Id("2555905") // Edited page
  val comment = "comment"

// object Confluence:
//   case class Request`
//   def request(apiToken: String): RequestT[Empty, Either[String, String], Any] =
//     basicRequest
//       .header("Authorization", s"Basic ${Base64.getEncoder.encodeToString(
//         s"${Configuration.userName}:${apiToken}".getBytes
//       )}")

//case class Version(number: Int)
//case class GetContentResponse(
//    version: Version,
//    title: String,
//    body: ContentBody
//)
//case class ContentBody(storage: ContentBodyStorage)
//case class ContentBodyStorage(value: String, representation: String)
//case class UpdateContentRequest(
//    version: Version,
//    `type`: String,
//    title: String,
//    body: ContentBody
//)

/** See [Confluence REST
  * API](https://developer.atlassian.com/cloud/confluence/rest/intro/)
  */
object Confluence:

  case class Token(value: String, domainName: String, userName: String)

  private def request(implicit
      token: Token
  ): PartialRequest[Either[String, String], Any] =
    basicRequest
      .header(
        "Authorization",
        s"Basic ${Base64.getEncoder
            .encodeToString(s"${token.userName}:${token.value}".getBytes)}"
      )

  private def prefix(implicit token: Token) =
    s"https://${token.domainName}/wiki/rest/api"

  type Request[T] =
    Token ?=> RequestT[Identity, Either[DecodingFailure, T], Any]

  object Space:

    opaque type Key = String

    object Key:
      def parse(value: String): Option[Key] = Some(value)

    object Property:
      opaque type Key = String

      object Key:
        def parse(value: String): Option[Key] = Some(value)

    def getSpaceProperty(
        key: Key,
        propertyKey: Property.Key
    ): Request[Option[String]] =
      request
        .get(uri"$prefix/space/$key/property/$propertyKey")
        .response(
          asJson[Json].map {
            case Left(e) => Right(None)
            case Right(json) =>
              json.hcursor.downField("value").as[Option[String]]
          }
        )

    def updateSpaceProperty(
        key: Key,
        propertyKey: Property.Key,
        value: String
    ): Request[Unit] =
      request
        .post(uri"$prefix/space/$key/property")
        .body(
          Json
            .obj(
              "key" -> Json.fromString(propertyKey.toString),
              "value" -> Json.fromString(value)
            )
        )
        .response(asJson[Json].getRight.map(_.hcursor.as[Unit]))

    def deleteSpaceProperty(
        key: Key,
        propertyKey: Property.Key
    ): Request[Unit] =
      request
        .delete(uri"$prefix/space/$key/property/$propertyKey")
        .response(ignore.map(_ => Right(())))

//object Content2:
//  opaque type Id = String
//  opaque type Version = Int
//  opaque type Title = String
//  opaque type StorageBody = String
//  opaque type UpdateError = String
//
//  opaque type Token = String
//
//  type Request[T] =
//    Token ?=> RequestT[Identity, Either[DecodingFailure, T], Any]
//
//  def Id(s: String): Id = s
//  def Token(s: String): Token = s
//  extension (v: Version) def increment: Version = v + 1
//
//  private val prefix =
//    s"https://${Configuration.domainName}/wiki/rest/api/content"
//

//
//  def getVersionRequest(id: Id): Request[Version] =
//    request
//      .get(uri"$prefix/$id?expand=version&trigger=")
//      .response(
//        asJson[Json].getRight
//          .map(_.hcursor.downField("version").downField("number").as[Version])
//      )

// case class Confluence(apiToken: String):
//   private val request = basicRequest
//     .header("Authorization", s"Basic ${Base64.getEncoder.encodeToString(
//       s"${Configuration.userName}:${apiToken}".getBytes
//     )}")

//   def attachmentRequest(file: File): RequestT[Identity, Unit, Any] =
//     request
//       .multipartBody(
//         multipartFile("file", file).fileName(file.getName),
//         multipart("comment", Configuration.comment)
//       )
//       .put(uri"https://${Configuration.domainName}/${List(
//         "wiki", "rest", "api", "content",
//         Configuration.contentId, "child", "attachment"
//       )}")
//       .response(asString.getRight.map(_ => ()))

//   def getContentRequest(id: String) =
//     request
//       .get(uri"https://${Configuration.domainName}/wiki/rest/api/content/${id}?expand=version,body.storage&trigger=")
//       .response(asJson[GetContentResponse])

//   def updateContentRequest(id: String, version: Int, title: String, content: String) =
//     request
//       .put(uri"https://${Configuration.domainName}/wiki/rest/api/content/${id}")
//       .body(UpdateContentRequest(Version(version), "page", title, ContentBody(ContentBodyStorage(content, "storage"))).asJson)
//       .response(asString.getRight.map(_ => ()))

//   def update2(request: UpdateContentRequest) =
//     request
//       .put(uri"https://${Configuration.domainName}/wiki/rest/api/content/${id}")
//       .body(request)
//       .response(asString.getRight.map(_ => ()))

@main def run(): Unit =
  given Confluence.Token = Confluence.Token(
    "",
    Configuration.domainName,
    Configuration.userName
  )
  given SttpBackend[Identity, Any] = OkHttpSyncBackend()
  given Main.RemoteSpace = Main.RemoteSpace(
    Configuration.domainName,
    Configuration.userName,
    Configuration.spaceId
  ) match {
    case Right(s) => s
    case Left(e)  => throw new Exception(e)
  }
  given Main.LocalDirectory =
    Main.LocalDirectory(this.getClass.getResource("/site").toURI) match {
      case Right(s) => s
      case Left(e)  => throw new Exception(e)
    }

  Main.resetMutex()
  println(Main.syncProcess())
//  given Content.Token = Content.Token("ieFA1qc81iPPK2rFJzxe02B9")
// val c = Confluence(token)
//  val backend = OkHttpSyncBackend()
// println(c.attachmentRequest(File("build.sbt")).send(backend))
// val response = c.getContentRequest(Configuration.contentId).send(backend).body.getOrElse(throw new Exception("no body"))
// val r = UpdateContentRequest(Version(response.version.number + 1), "page", title, ContentBody(ContentBodyStorage(content, "storage"))
// println(c.updateContentRequest(Configuration.contentId, response.version.number + 1, response.title, "bar").send(backend))
//  println(
//    Content
//      .getVersionRequest(Configuration.contentId)
//      .send(backend)
//      .body
//      .map(_.increment)
//  )
