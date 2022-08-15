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
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

class ConfluenceSuite extends munit.FunSuite:

  val integration = munit.Tag("integration")

  given Access = Access(
    sys.env.get("ATLASSIAN_API_TOKEN").flatMap(Token.from).get,
    Domain.from("sanderd.atlassian.net").get,
    User.from("mail+docpkg-ci@sanderdijkhuis.nl").get
  )

  val backend = HttpClientSyncBackend()
  val space = SpaceKey.parse("DOCPKGIT").get

  test("gets a space property".tag(integration).ignore) {
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

  test("uploads and gets attachments".tag(integration)) {
    val title = Title.parse(UUID.randomUUID().toString()).get
    val body = Body.parse("").get
    val attachmentName = AttachmentName.get("attachment.txt").get
    val attachmentData = "Hello, World!\n".getBytes(StandardCharsets.UTF_8)
    val path = Files.createTempFile(Paths.get("/tmp"), "attachment", ".txt")
    val attachment = Attachment(attachmentName, path)
    val comment = Comment.from("test-comment").get

    Files.write(path, attachmentData)
    val contentId = createPage(space, title, body).send(backend).body
    val attachmentId = attach(contentId, attachment, comment).send(backend).body
    Files.delete(path)
    val out = getAttachment(contentId, attachmentId).send(backend).body

    assertEquals(out.toSeq, attachmentData.toSeq)

    deletePage(contentId).send(backend)
  }

  test("updates pages".tag(integration)) {
    val title = Title.parse(UUID.randomUUID().toString()).get
    val body1 = Body.parse("one").get
    val body2 = Body.parse("two").get

    val id = createPage(space, title, body1).send(backend).body
    val content1 = getContentForUpdate(id).send(backend).body
    updatePage(id, content1.version.increment, title, body2).send(backend)
    val content2 = getContentForUpdate(id).send(backend).body

    assertEquals(content2.body, body2)

    deletePage(id).send(backend)
  }

  test("lists pages without throwing an error".tag(integration)) {
    // TODO: test behavior

    val key = PropertyKey.parse("test-key").get
    val response = getPagesWithProperty(key).send(backend).body
  }
