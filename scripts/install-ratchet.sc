#!/usr/bin/env -S scala-cli shebang

import java.io.{ByteArrayOutputStream, File}
import java.net.URL
import java.nio.file.Files
import java.security.MessageDigest
import sys.process.*

def bytes(hex: String) =
  hex.sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)

val architecture =
  "uname -s".!!.trim.toLowerCase + "_" + ("uname -m".!!.trim match {
    case "x86_64" => "amd64"
  })

val checksums = Map(
  "darwin_amd64" -> bytes(
    "58b6eff85bbc995acf989bf9a15aa6c0ebdb9c1e76a692c91695059dc3bde7036e74ba9eca61f62cbaddc2f6eba12cda9577ce172d66cb18fa13e61e757bfcdf"
  ),
  "linux_amd64" -> bytes(
    "68c3399c2698042db1764eb4d108ff2904fc60ca9ac644b4f5f51e15a9b4e40a50e7c2b6c0217497deb7697234bb2443003e0f815463570417c4947013f198fe"
  )
)
val version = "0.2.3"
val url = URL(
  s"https://github.com/sethvargo/ratchet/releases/download/v$version/ratchet_${version}_$architecture.tar.gz"
)
val directory = File("target/ratchet")
val download = File(directory, "download.tar.gz")
val executable = "ratchet"

directory.mkdirs()

(url #> download).!!

val bytes = Files.readAllBytes(download.toPath)
val digest = MessageDigest.getInstance("SHA-512").digest(bytes)

if ((checksums(architecture) diff digest).nonEmpty)
  println("Checksum did not match")
  sys.exit(1)

s"tar -xzf ${download.getPath} --directory ${directory.getPath} $executable".!!

val voidLogger = ProcessLogger(_ => (), _ => ())

if (File(directory, executable).getPath.!(voidLogger) != 0)
  println("Could not launch ratchet")
  sys.exit(1)
