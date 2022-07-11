package nl.sanderdijkhuis.docpkg

import nl.sanderdijkhuis.docpkg.ContentManagement.{AttachmentName, PageName}

class ContentManagementSuite extends munit.FunSuite:

  private val correctNames = List("foo", "foo-bar", "Foo", "foo42")
  private val incorrectNames = List("foo bar", "foo$", "")

  test("parses correct PageName values") {
    for s <- correctNames do assert(PageName.get(s).nonEmpty)
  }

  test("rejects incorrect PageName values") {
    for s <- incorrectNames do assert(PageName.get(s).isEmpty)
  }

  test("parses correct AttachmentName values") {
    for s <- correctNames do assert(AttachmentName.get(s).nonEmpty)
  }

  test("rejects incorrect AttachmentName values") {
    for s <- incorrectNames do assert(AttachmentName.get(s).isEmpty)
  }
