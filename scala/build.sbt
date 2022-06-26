ThisBuild / scalaVersion := "3.1.3"

name := "docpkg"
version := "0.1.0-SNAPSHOT"

libraryDependencies ++= Seq(
  "com.softwaremill.sttp.client3" %% "core" % "3.6.2",
  "com.softwaremill.sttp.client3" %% "okhttp-backend" % "3.6.2",
  "com.softwaremill.sttp.client3" %% "circe" % "3.6.2",
  "io.circe" %% "circe-generic" % "0.14.1",
  "org.scalactic" %% "scalactic" % "3.2.12",
  "org.scalatest" %% "scalatest" % "3.2.12" % "test"
)

//lazy val confluenceApi: Project = project
//  .in(file("confluence-api"))
//  .settings(
//    openApiInputSpec := s"${baseDirectory.value.getPath}/confluence-api.json",
//    openApiGeneratorName := "scala-sttp",
//    openApiOutputDir := baseDirectory.value.name,
//    openApiIgnoreFileOverride := s"${baseDirectory.in(ThisBuild).value.getPath}/openapi-ignore-file",
//    libraryDependencies ++= Seq(
//      "com.softwaremill.sttp.client3" %% "core" % "3.6.2",
//      "com.softwaremill.sttp.client3" %% "json4s" % "3.6.2",
//      "org.json4s" %% "json4s-jackson" % "3.6.8"
//    ),
//    (compile in Compile) := ((compile in Compile) dependsOn openApiGenerate).value,
//    cleanFiles += baseDirectory.value / "src"
//  )
