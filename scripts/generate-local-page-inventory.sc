//> using file "../src"

import docpkg.content.*
import docpkg.inventory.*

if args.length != 1 then
  println("Specify a folder name as argument")
  sys.exit(1)

val path = java.nio.file.Paths.get(args(0))
val result = traverseDepthFirst(path)
val traversal = result.fold(e => throw e, identity)
val pages = inventory(traversal)

for (p <- pages.toList)
  println(s"Page path: ${p.path.toStringPath}")
  println(s"Page content: ${p.content}")
  if p.attachments.length > 0 then println("Attachments:")
  for (a <- p.attachments)
    println(s"- ${a.name} -> ${a.file}")
  println()
