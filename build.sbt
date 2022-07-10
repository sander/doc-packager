scalaVersion := "3.1.3"

organization := "nl.sanderdijkhuis"
name := "docpkg"

versionScheme := Some("semver-spec")
version := "0.1.0-SNAPSHOT"

githubOwner := "sander"
githubRepository := "docpkg"
githubTokenSource := TokenSource.GitConfig("github.token") || TokenSource
  .Environment("PACKAGE_GITHUB_TOKEN")

libraryDependencies ++= Seq(
  "junit" % "junit" % "4.13.2" % Test,
  "com.github.sbt" % "junit-interface" % "0.13.3" % Test,
  "io.cucumber" %% "cucumber-scala" % "8.5.1" % Test,
  "io.cucumber" % "cucumber-junit" % "7.4.1" % Test
)
