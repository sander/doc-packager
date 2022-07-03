package nl.sanderdijkhuis.docpkg

import sttp.client3.*
import sttp.client3.circe.*
import sttp.client3.okhttp.OkHttpSyncBackend
import sttp.monad.syntax.*
import io.circe.generic.auto.*
import io.circe.*
import io.circe.Decoder.Result
import io.circe.syntax.*
import nl.sanderdijkhuis.docpkg.Confluence.Space.Property.Key
import nl.sanderdijkhuis.docpkg.Main.Content.{Id, Version}
import nl.sanderdijkhuis.docpkg.Main.RemoteSpace.UpdatablePageContent
import sttp.model.Uri

import java.io.File
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.util.{Base64, UUID}
import scala.annotation.tailrec
import scala.collection.immutable.ListMap
import scala.io.Source

object Main:

  opaque type Name = String
  opaque type Path = List[Name]
  opaque type Attachment = File

  case class Content(title: Content.Title, body: Content.Body)

  object Name:
    def apply(p: String): Name = p

  object Path:
    def apply(p: List[Name]): Path = p

  object Content:
    opaque type Id = String
    opaque type Title = String
    opaque type Body = String
    opaque type Version = Int

    extension (v: Version)
      def increment: Version = v + 1
      def value: Int = v
    extension (t: Title) def toString: String = t
    extension (b: Body) def value: String = b

    def Version(value: Int): Version = value

    def parse(title: String, body: String): Option[Content] = Some(
      Content(title, body)
    )

    object Id:
      def parse(value: String): Option[Id] = Some(value)

  trait LocalDirectory:
    def traverse(): LocalDirectory.BreadthFirstTraversal
    def getPageContent(path: Path): Content
    def getAttachment(path: Path, name: Name): Attachment

  object LocalDirectory:
    type BreadthFirstTraversal = ListMap[Path, List[Name]]

    extension (l: BreadthFirstTraversal)
      def hasAttachment(path: Path, name: Name): Boolean =
        l.get(path).map(_.collectFirst(_ == name)).nonEmpty

    def apply(rootPath: URI): Either[String, LocalDirectory] =
      val d = File(rootPath)
      val pageSuffix = ".html"
      val mainPageName = s"index$pageSuffix"
      if (!d.isDirectory) Left("No directory")
      else
        Right(
          new LocalDirectory {
            override def traverse(): BreadthFirstTraversal = {
              def traverse(
                  path: Path,
                  directory: File
              ): ListMap[Path, List[Name]] =
                val root: ListMap[Path, List[Name]] = ListMap(path -> (for
                  f <- directory.listFiles().toList
                  if !f.isDirectory && !f.getName.endsWith(pageSuffix)
                yield f.getName))
                val directories = ListMap.from(for
                  f <- directory.listFiles().toList if f.isDirectory
                  n = f.getName :: path
                  e <- traverse(n, f)
                yield e)
                val pages = ListMap.from(for
                  f <- directory.listFiles().toList
                  if !f.isDirectory && f.getName.endsWith(
                    pageSuffix
                  ) && (f.getName != mainPageName)
                yield (f.getName :: path) -> Nil)
                root ++ directories ++ pages
              traverse(Nil, d)
            }

            override def getPageContent(path: Path): Content =
              val p = URI(rootPath.toString + "/" + path.reverse.mkString("/"))
              val title = path match {
                case Nil => "root page"
                case p   => p.mkString("-")
              }
              File(p) match {
                case f if f.isDirectory =>
                  val x = f.listFiles().find(_.getName == mainPageName)
                  x match {
                    case Some(x) =>
                      val s = Source.fromFile(x)
                      val c = s.getLines().mkString
                      s.close()
                      Content.parse(title, c).get
                    case None =>
                      Content.parse(title, "no content").get
                  }
                case f if !f.isDirectory =>
                  val s = Source.fromFile(f)
                  val c = s.getLines().mkString
                  s.close()
                  Content.parse(title, c).get
              }

            override def getAttachment(path: Path, name: Name): Attachment =
              val p = URI(
                rootPath.toString + "/" + path.reverse.mkString(
                  "/"
                ) + "/" + name
              )
              File(p)
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
    def getUpdatableAttachmentContent(
        contentId: Content.Id,
        attachmentId: Confluence.Content.AttachmentId
    ): RemoteSpace.UpdatableAttachmentContent
    def updatePageContent(
        contentId: Content.Id,
        version: Content.Version,
        content: Content
    ): Unit
    def appendTo(
        pageId: Content.Id,
        targetId: Content.Id
    ): Unit

  object RemoteSpace:
    private val prefix =
      "docpkg" // TODO enable namespacing, to have multiple docpkg trees in 1 space

    case class RemoteContent(
        id: Content.Id,
        attachments: Map[Name, Confluence.Content.AttachmentId]
    )

    type Inventory = Map[Path, RemoteContent]

    extension (r: Inventory)
      def hasAttachment(path: Path, name: Name): Boolean =
        r.get(path).map(_.attachments.keys.collectFirst(_ == name)).nonEmpty

    case class UpdatablePageContent(
        version: Content.Version,
        content: Content
    )

    case class UpdatableAttachmentContent(content: Array[Byte]) {
      def contentEquals(file: File): Boolean =
        val ba = Files.readAllBytes(file.toPath)
        (ba diff content).isEmpty
    }

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
                .parse(s"${prefix}_mutex")
                .getOrElse(throw new Exception("Could not parse key"))

            val pathKey: Confluence.Space.Property.Key =
              Confluence.Space.Property.Key
                .parse(s"${prefix}_path")
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

            override def list(): Inventory =
              val request = Confluence.Content.get(pathKey)
              @tailrec def get(
                  req: Confluence.Request[
                    Confluence.Content.GetContentResponse
                  ],
                  i: Inventory
              ): Inventory =
                val r: Confluence.Content.GetContentResponse = req
                  .send(backend)
                  .body
                  .getOrElse(throw new Exception("Could not release mutex"))
                r.next match {
                  case None    => i ++ r.inventory
                  case Some(n) => get(n, i ++ r.inventory)
                }

              get(request, Map.empty)

            override def create(path: Path, content: Content): Main.Content.Id =
              val id = Confluence.Content
                .createPage(content.title, sk, content.body)
                .send(backend)
                .body
                .getOrElse(throw new Exception("Could not create page"))
              Confluence.Content
                .createProperty(id, pathKey, path.map(_.toString))
                .send(backend)
              id

            override def upload(
                contentId: Main.Content.Id,
                name: Name,
                attachment: Attachment
            ): Unit = Confluence.Content
              .attach(contentId, name, attachment)
              .send(backend)

            override def deletePage(contentId: Main.Content.Id): Unit = ???

            override def deleteAttachment(
                contentId: Main.Content.Id,
                name: Name
            ): Unit = ???

            override def getUpdatablePageContent(
                contentId: Main.Content.Id
            ): UpdatablePageContent = Confluence.Content
              .getContentForUpdate(contentId)
              .send(backend)
              .body
              .getOrElse(throw new Exception("Could not get content"))

            override def getUpdatableAttachmentContent(
                contentId: Id,
                attachmentId: Confluence.Content.AttachmentId
            ): UpdatableAttachmentContent =
              val att: Array[Byte] = Confluence.Content
                .getAttachment(contentId, attachmentId)
                .send(backend)
                .body
                .getOrElse(throw new Exception("Could not get attachment"))
              UpdatableAttachmentContent(att)

            override def updatePageContent(
                contentId: Main.Content.Id,
                version: Version,
                content: Content
            ): Unit = Confluence.Content
              .updatePage(contentId, version, content)
              .send(backend)

            override def appendTo(
                pageId: Main.Content.Id,
                targetId: Main.Content.Id
            ): Unit =
              Confluence.Content.appendTo(pageId, targetId).send(backend)
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
      (n, _) <- c.attachments if !source.hasAttachment(p, n)
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
    val updatedAttachments = for // TODO check if it really works
      (p, ns) <- source.toList
      n <- ns if target.hasAttachment(p, n)
      a = local.getAttachment(p, n)
      b = remote.getUpdatableAttachmentContent(
        target(p).id,
        target(p).attachments(n)
      )
      if (!b.contentEquals(a))
    yield
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

    extension (k: Key) def toString: String = k

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

  object Content:

    opaque type AttachmentId = String

    def AttachmentId(value: String): AttachmentId = value

    def createPage(
        title: Main.Content.Title,
        space: Space.Key,
        content: Main.Content.Body
    ): Request[Main.Content.Id] =
      request
        .post(uri"$prefix/content")
        .body(
          Json.obj(
            "title" -> Json.fromString(title.toString),
            "type" -> Json.fromString("page"),
            "space" -> Json.obj("key" -> Json.fromString(space.toString)),
            "body" -> Json.obj(
              "storage" -> Json.obj(
                "representation" -> Json.fromString("storage"),
                "value" -> Json.fromString(content.value)
              )
            )
          )
        )
        .response(
          asJson[Json].getRight.map(r =>
            r.hcursor
              .downField("id")
              .as[String]
              .map(_.asInstanceOf[Main.Content.Id])
          )
        )

    def attach(
        id: Main.Content.Id,
        name: Main.Name,
        attachment: Main.Attachment
    ): Request[Unit] =
      request
        .multipartBody(
          multipartFile("file", attachment.asInstanceOf[File])
            .fileName(name.asInstanceOf[String]),
          multipart("comment", Configuration.comment)
        )
        .put(uri"$prefix/content/$id/child/attachment")
        .response(asString.getRight.map(_ => Right(())))

    def appendTo(id: Main.Content.Id, target: Main.Content.Id): Request[Unit] =
      request
        .put(uri"$prefix/content/$id/move/append/$target")
        .response(asString.getRight.map(_ => Right(())))

    def createProperty(
        id: Main.Content.Id,
        key: Space.Property.Key,
        value: List[String]
    ): Request[Unit] = request
      .post(uri"$prefix/content/$id/property")
      .body(
        Json.obj(
          "key" -> Json.fromString(key.toString),
          "value" -> Json.arr(value.map(v => Json.fromString(v)): _*)
        )
      )
      .response(asString.getRight.map(_ => Right(())))

    case class GetContentResponse(
        next: Option[Request[GetContentResponse]],
        inventory: Main.RemoteSpace.Inventory,
        start: Int,
        limit: Int,
        size: Int
    )

    def get(
        propertyKey: Space.Property.Key,
        offset: Int = 0
    ): Request[GetContentResponse] =
      val lim = 50
      val decodeItem
          : Decoder[Option[(Main.Path, Main.RemoteSpace.RemoteContent)]] =
        Decoder.instance(a =>
          for {
            id <- a.downField("id").as[String]
            path <- a
              .downField("metadata")
              .downField("properties")
              .downField(propertyKey.toString)
              .downField("value")
              .as[Option[Array[String]]]
            attachments <- a
              .downField("children")
              .downField("attachment")
              .downField("results")
              .as[Option[List[(String, String)]]](
                Decoder.decodeOption(
                  Decoder.decodeList(
                    for {
                      id <- Decoder[String].prepare(_.downField("id"))
                      name <- Decoder[String].prepare(_.downField("title"))
                    } yield (id, name)
                  )
                )
              )
          } yield path match {
            case Some(path) =>
              Some(
                (
                  Main.Path(path.toList.map(s => Main.Name(s))),
                  Main.RemoteSpace
                    .RemoteContent(
                      Main.Content.Id.parse(id).get,
                      Map.from(attachments.getOrElse(List.empty).map {
                        case (id, name) =>
                          Main.Name(name) -> Confluence.Content.AttachmentId(id)
                      })
                    )
                )
              )
            case None => None
          }
        )
      val decodeItems
          : Decoder[List[(Main.Path, Main.RemoteSpace.RemoteContent)]] =
        Decoder
          .decodeList[Option[(Main.Path, Main.RemoteSpace.RemoteContent)]](
            decodeItem
          )
          .map(_.flatMap(_.toList))
      request
        .get(
          uri"$prefix/content?limit=$lim&start=$offset&type=page&expand=metadata.properties.$propertyKey,children.attachment"
        )
        .response(
          asJson[Json].getRight
            .map(j =>
              for {
                start <- j.hcursor.downField("start").as[Int]
                size <- j.hcursor.downField("size").as[Int]
                limit <- j.hcursor.downField("limit").as[Int]
                next <- j.hcursor
                  .downField("_links")
                  .downField("next")
                  .as[Option[String]]
                inv <- j.hcursor
                  .downField("results")
                  .as[List[(Main.Path, Main.RemoteSpace.RemoteContent)]](
                    decodeItems
                  )
              } yield GetContentResponse(
                next.map(_ => get(propertyKey, offset + limit)),
                Map.from(inv),
                start,
                size,
                limit
              )
            )
        )

    def getContentForUpdate(
        id: Main.Content.Id
    ): Request[UpdatablePageContent] =
      request
        .get(uri"$prefix/content/$id?expand=version,body.storage&trigger=")
        .response(
          asJson[Json].getRight.map(j =>
            for {
              title <- j.hcursor.downField("title").as[String]
              version <- j.hcursor
                .downField("version")
                .downField("number")
                .as[Int]
              body <- j.hcursor
                .downField("body")
                .downField("storage")
                .downField("value")
                .as[String]
              c = Main.Content.parse(title, body).get
            } yield UpdatablePageContent(
              Main.Content.Version(version),
              c
            )
          )
        )

    def getAttachment(
        contentId: Main.Content.Id,
        attachmentId: AttachmentId
    ): Request[Array[Byte]] = request
      .get(
        uri"$prefix/content/$contentId/child/attachment/$attachmentId/download"
      )
      .followRedirects(true)
      .response(asByteArray.map {
        case Left(e)  => Left(DecodingFailure(e, List.empty))
        case Right(r) => Right(r)
      })

    def updatePage(
        id: Main.Content.Id,
        version: Main.Content.Version,
        content: Main.Content
    ): Request[Unit] =
      request
        .put(uri"$prefix/content/$id")
        .body(
          Json.obj(
            "title" -> Json.fromString(content.title.toString),
            "type" -> Json.fromString("page"),
            "version" -> Json.obj("number" -> Json.fromInt(version.value)),
            "body" -> Json.obj(
              "storage" -> Json.obj(
                "representation" -> Json.fromString("storage"),
                "value" -> Json.fromString(content.body.toString)
              )
            )
          )
        )
        .response(asString.getRight.map(_ => Right(())))

    // def updateSpaceProperty(
//        key: Key,
//        propertyKey: Property.Key,
//        value: String
//    ): Request[Unit] =
//      request
//        .post(uri"$prefix/space/$key/property")
//        .body(
//          Json
//            .obj(
//              "key" -> Json.fromString(propertyKey.toString),
//              "value" -> Json.fromString(value)
//            )
//        )
//        .response(asJson[Json].getRight.map(_.hcursor.as[Unit]))

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
    "shkpoKEN8HizzReZCoq39532",
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
//  println(summon[Main.RemoteSpace].list())
  println(Main.syncProcess())

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
