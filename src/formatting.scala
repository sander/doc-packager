//> using lib "org.scala-lang.modules::scala-xml:2.1.0"

package docpkg.formatting

import java.nio.file.Path
import scala.xml.Elem

case class Page(value: Elem)

def directoryPage(): Page =
  Page(<ac:structured-macro ac:name="children" />)
