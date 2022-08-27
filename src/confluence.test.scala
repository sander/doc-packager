//> using testFramework "munit.Framework"
//> using lib "com.softwaremill.sttp.client3::core:3.7.4"

package docpkg

import docpkg.confluence.*
import docpkg.content.*

import sttp.client3.*

import java.util.UUID
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

class ConfluenceSuite extends munit.FunSuite:

  val integration = munit.Tag("integration")

  val tokenName = "ATLASSIAN_API_TOKEN"
  val token = sys.env
    .get(tokenName)
    .flatMap(Token.from)
    .getOrElse(sys.error(s"Set the $tokenName environment variable"))

  given Access = Access(
    sys.env.get("ATLASSIAN_API_TOKEN").flatMap(Token.from).get,
    Domain.from("sanderd.atlassian.net").get,
    User.from("mail+docpkg-ci@sanderdijkhuis.nl").get
  )

  val backend = HttpClientSyncBackend()
  val space = SpaceKey.parse("DOCPKGIT").get
  val propertyKey = PropertyKey.parse("test-key").get

  val page = FunFixture[Id](
    setup = { test =>
      val uuid = UUID.randomUUID().toString()
      val title = Title.parse(s"${test.name} ($uuid)").get
      val body = Body.parse("").get

      createPage(space, title, body).send(backend).body
    },
    teardown = { id => deletePage(id).send(backend) }
  )

  page.test("creates and deletes pages".tag(integration)) { id => () }

  page.test("uploads and gets attachments".tag(integration)) { contentId =>
    val attachmentName = AttachmentName.get("attachment.txt").get
    val attachmentData = "Hello, World!\n".getBytes(StandardCharsets.UTF_8)
    val path = Files.createTempFile(Paths.get("/tmp"), "attachment", ".txt")
    val attachment = Attachment(attachmentName, path)
    val comment = Comment.from("test-comment").get

    Files.write(path, attachmentData)
    val attachmentId = attach(contentId, attachment, comment).send(backend).body
    Files.delete(path)
    val out = getAttachment(contentId, attachmentId).send(backend).body

    assertEquals(out.toSeq, attachmentData.toSeq)
  }

  page.test("updates pages".tag(integration)) { id =>
    val body2 = Body.parse("two").get

    val content1 = getContentForUpdate(id).send(backend).body
    updatePage(id, content1.version.increment, content1.title, body2)
      .send(backend)
    val content2 = getContentForUpdate(id).send(backend).body

    assertEquals(content2.body, body2)
  }

  FunFixture
    .map2(page, page)
    .test("moves pages in the navigation menu without error".tag(integration)) {
      (id1, id2) =>
        appendTo(id1, id2).send(backend)
    }

  page.test("creates and finds page properties".tag(integration)) { id =>
    val uuid = UUID.randomUUID().toString()
    val propertyKey = PropertyKey.parse(s"test-$uuid").get

    createProperty(id, propertyKey, List("testing")).send(backend)

    val response = getPagesWithProperty(propertyKey).send(backend).body

    assert(response.inventory.values.exists(_.id == id))
  }
