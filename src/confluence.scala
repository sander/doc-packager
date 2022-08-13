//> using lib "com.softwaremill.sttp.client3::core:3.6.2"
//> using lib "com.softwaremill.sttp.client3::circe:3.6.2"
//> using lib "io.circe::circe-generic:0.14.1"

package docpkg.confluence

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

case class Access(token: Token, domainName: Domain, userName: User)

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

def getSpaceProperty(s: SpaceKey, p: PropertyKey): Request[Option[String]] =
  request
    .get(uri"$prefix/space/$s/property/$p")
    .response(
      asJson[Json].map {
        case Left(x) => Right(None)
        case Right(json) =>
          json.hcursor.downField("value").as[Option[String]]
      }.getRight
    )

def updateSpaceProperty(s: SpaceKey, k: PropertyKey, v: String): Request[Unit] =
  request
    .post(uri"$prefix/space/$s/property")
    .body(
      Json.obj(
        "key" -> Json.fromString(k.toString),
        "value" -> Json.fromString(v)
      )
    )
    .response(asJson[Json].getRight.map(_.hcursor.as[Unit]))
