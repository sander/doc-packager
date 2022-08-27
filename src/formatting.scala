//> using lib "org.scala-lang.modules::scala-xml:2.1.0"

package docpkg.formatting

import java.nio.file.Path
import scala.xml.Elem

case class File private (content: String)
object File:
  def from(content: String): Option[File] = Some(File(content))

case class Page private (contents: List[Elem])
object Page:
  def from(contents: List[Elem]): Option[Page] = Some(Page(contents))
  def index: Page = Page(List(<ac:structured-macro ac:name="children" />))
  def error(originalContent: String): Page = Page(
    List(
      <p>Could not create page. Original content:</p>,
      <pre>{originalContent}</pre>
    )
  )
  def apply(file: File): Page =
    Page
      .from(List(<pre>{file.content}</pre>))
      .getOrElse(Page.error(file.content))
