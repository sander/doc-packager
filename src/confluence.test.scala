//> using testFramework "munit.Framework"
//> using lib "com.softwaremill.sttp.client3::core:3.7.4"
//> using lib "com.softwaremill.sttp.client3::scribe-backend:3.7.4"
//> using lib "com.outr::scribe:3.10.2"

package docpkg

import docpkg.confluence.*
import docpkg.content.*

import sttp.client3.*
import sttp.client3.logging.scribe.ScribeLoggingBackend

import java.util.UUID

class ConfluenceSuite extends munit.FunSuite:

  val integration = munit.Tag("integration")

  given Access = Access(
    sys.env.get("ATLASSIAN_API_TOKEN").flatMap(Token.from).get,
    Domain.from("sanderd.atlassian.net").get,
    User.from("mail+docpkg-ci@sanderdijkhuis.nl").get
  )

  val backend = ScribeLoggingBackend(HttpClientSyncBackend())
  val space = SpaceKey.parse("DOCPKGIT").get

  test("gets a space property".tag(integration)) {
    val property = PropertyKey.parse("non-existent-test-property").get
    val request = getSpaceProperty(space, property)

    val response = request.send(backend).body

    assert(response.isEmpty)
  }

  test("updates a space property effectively".tag(integration).ignore) {
    // TODO: updateSpaceProperty 404, probably because of required admin rights

    val property = PropertyKey.parse("test-property").get
    val value = UUID.randomUUID().toString()
    val update = updateSpaceProperty(space, property, value)
    val get = getSpaceProperty(space, property)

    update.send(backend)
    val response = get.send(backend).body

    assert(response.contains(value))
  }

  test("creates and deletes pages".tag(integration)) {
    val title = Title.parse(UUID.randomUUID().toString()).get
    val body = Body.parse("").get

    val id = createPage(space, title, body).send(backend).body

    deletePage(id).send(backend)
  }
