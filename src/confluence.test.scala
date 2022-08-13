//> using testFramework "munit.Framework"
//> using lib "com.softwaremill.sttp.client3::core:3.7.4"

package docpkg

import docpkg.confluence.*

import sttp.client3.*

class ConfluenceSuite extends munit.FunSuite:

  val integration = munit.Tag("integration")

  test("gets a space property".tag(integration)) {
    given Access = Access(
      sys.env.get("ATLASSIAN_API_TOKEN").flatMap(Token.from).get,
      Domain.from("sanderqd.atlassian.net").get,
      User.from("mail+docpkg-ci@sanderdijkhuis.nl").get
    )
    val backend = HttpClientSyncBackend()
    val space = SpaceKey.parse("DOCPKGIT").get
    val property = PropertyKey.parse("non-existent-test-property").get
    val request = getSpaceProperty(space, property)

    val response = request.send(backend).body

    assert(response.isEmpty)
  }
