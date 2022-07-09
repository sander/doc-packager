#!/usr/bin/env -S scala-cli shebang

import java.io.{File, IOException}
import sys.process.*

val ratchet = "target/ratchet/ratchet"
val voidLogger = ProcessLogger(_ => (), _ => ())

try ratchet.!(voidLogger)
catch
  case _: IOException =>
    println("First run install-ratchet.sc")
    sys.exit(1)

val paths = for
  f <- File(".github/workflows").listFiles.toList
  if f.isFile && f.getName.endsWith(".yml")
yield f.getPath

val exitCodes = for p <- paths yield
  println(s"Checking $p...")
  s"$ratchet check $p".!

for code <- exitCodes if code != 0 yield sys.exit(code)
