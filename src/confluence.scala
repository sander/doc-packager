//> using lib "com.softwaremill.sttp.client3::core:3.6.2"
//> using lib "com.softwaremill.sttp.client3::circe:3.6.2"
//> using lib "io.circe::circe-generic:0.14.1"

package docpkg.confluence

import docpkg.content.*

import io.circe.*
import io.circe.syntax.*
import sttp.client3.*
import sttp.client3.circe.*
import sttp.monad.syntax.*

import java.util.Base64

opaque type Token = String
object Token:
  def from(value: String): Option[Token] = Some(value)

opaque type Domain = String
object Domain:
  def from(value: String): Option[Domain] = Some(value)

opaque type User = String
object User:
  def from(value: String): Option[User] = Some(value)

opaque type Comment = String
object Comment:
  def from(value: String): Option[Comment] = Some(value)

opaque type AttachmentId = String
object AttachmentId:
  def from(value: String): Option[AttachmentId] = Some(value)

case class Access(token: Token, domainName: Domain, userName: User)

case class UpdatablePageContent(version: Version, title: Title, body: Body)

case class RemoteContent(
    id: Id,
    attachments: Map[AttachmentName, AttachmentId]
)

case class GetContentResponse(
    next: Option[Request[GetContentResponse]],
    inventory: Map[PagePath, RemoteContent],
    start: Int,
    limit: Int,
    size: Int
)

private val request: Access ?=> PartialRequest[Either[String, String], Any] =
  val Access(value, _, userName) = summon[Access]
  val credential =
    Base64.getEncoder.encodeToString(s"$userName:$value".getBytes)
  basicRequest.header("Authorization", s"Basic $credential")

private def prefix(implicit token: Access) =
  s"https://${token.domainName}/wiki/rest/api"

type Request[A] = Access ?=> RequestT[Identity, A, Any]

opaque type SpaceKey = String
object SpaceKey:
  def parse(value: String): Option[SpaceKey] = Some(value)
extension (k: SpaceKey) def toString: String = k

opaque type PropertyKey = String
object PropertyKey:
  def parse(value: String): Option[PropertyKey] = Some(value)

def createPage(s: SpaceKey, t: Title, b: Body): Request[Id] =
  request
    .post(uri"$prefix/content")
    .body(
      Json.obj(
        "title" -> Json.fromString(t.toString),
        "type" -> Json.fromString("page"),
        "space" -> Json.obj("key" -> Json.fromString(s.toString)),
        "ancestors" -> Json.arr(Json.obj("id" -> Json.fromString("4882495"))),
        "body" -> Json.obj(
          "storage" -> Json.obj(
            "representation" -> Json.fromString("storage"),
            "value" -> Json.fromString(b.value)
          )
        )
      )
    )
    .response(
      asJson[Json].getRight
        .map(r => r.hcursor.downField("id").as[String].map(_.asInstanceOf[Id]))
        .getRight
    )

def deletePage(id: Id): Request[Unit] =
  request
    .delete(uri"$prefix/content/$id")
    .response(asString.getRight.map(_ => ()))

def attach(id: Id, a: Attachment, c: Comment): Request[AttachmentId] =
  request
    .multipartBody(
      multipartFile("file", a.file).fileName(a.name.toString),
      multipart("comment", c)
    )
    .put(uri"$prefix/content/$id/child/attachment")
    .response(
      asJson[Json].getRight
        .map(r =>
          r.hcursor
            .downField("results")
            .downArray
            .downField("id")
            .as[String]
            .map(_.asInstanceOf[AttachmentId])
        )
        .getRight
    )

def getAttachment(c: Id, a: AttachmentId): Request[Array[Byte]] = request
  .get(uri"$prefix/content/$c/child/attachment/$a/download")
  .response(asByteArrayAlways)
  .followRedirects(true)

def getContentForUpdate(id: Id): Request[UpdatablePageContent] =
  request
    .get(uri"$prefix/content/$id?expand=version,body.storage&trigger=")
    .response(
      asJson[Json].getRight
        .map(j =>
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
          } yield UpdatablePageContent(
            Version.from(version).get,
            Title.parse(title).get,
            Body.parse(body).get
          )
        )
        .getRight
    )

def updatePage(c: Id, v: Version, t: Title, b: Body): Request[Unit] =
  request
    .put(uri"$prefix/content/$c")
    .body(
      Json.obj(
        "title" -> Json.fromString(t.toString),
        "type" -> Json.fromString("page"),
        "version" -> Json.obj("number" -> Json.fromInt(v.value)),
        "body" -> Json.obj(
          "storage" -> Json.obj(
            "representation" -> Json.fromString("storage"),
            "value" -> Json.fromString(b.toString)
          )
        )
      )
    )
    .response(ignore)

def getPagesWithProperty(
    p: PropertyKey,
    offset: Int = 0
): Request[GetContentResponse] =
  val lim = 50
  val decodeItem: Decoder[Option[(PagePath, RemoteContent)]] =
    Decoder.instance(a =>
      for
        id <- a.downField("id").as[String]
        path <- a
          .downField("metadata")
          .downField("properties")
          .downField(p.toString)
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
      yield path match
        case Some(path) =>
          Some(
            (
              PagePath.from(path.toList.map(s => PageName.from(s))).get,
              RemoteContent(
                Id.parse(id).get,
                Map.from(attachments.getOrElse(List.empty).map {
                  case (id, name) =>
                    AttachmentName.from(name) -> AttachmentId.from(id).get
                })
              )
            )
          )
        case None => None
    )
  val decodeItems: Decoder[List[(PagePath, RemoteContent)]] =
    Decoder
      .decodeList[Option[(PagePath, RemoteContent)]](decodeItem)
      .map(_.flatMap(_.toList))
  request
    .get(
      uri"$prefix/content?limit=$lim&start=$offset&type=page&expand=metadata.properties.$p,children.attachment"
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
              .as[List[(PagePath, RemoteContent)]](decodeItems)
          } yield GetContentResponse(
            next.map(_ => getPagesWithProperty(p, offset + limit)),
            Map.from(inv),
            start,
            limit,
            size
          )
        )
        .getRight
    )

def appendTo(id: Id, target: Id): Request[Unit] =
  request.put(uri"$prefix/content/$id/move/append/$target").response(ignore)

def createProperty(id: Id, k: PropertyKey, v: List[String]): Request[Unit] =
  request
    .post(uri"$prefix/content/$id/property")
    .body(
      Json.obj(
        "key" -> Json.fromString(k.toString),
        "value" -> Json.arr(v.map(v => Json.fromString(v)): _*)
      )
    )
    .response(ignore)
