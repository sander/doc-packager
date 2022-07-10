scalaVersion := "3.1.3"

organization := "nl.sanderdijkhuis"
name := "docpkg"

versionScheme := Some("semver-spec")
version := "0.1.0-SNAPSHOT"

githubOwner := "sander"
githubRepository := "docpkg"
githubTokenSource := TokenSource.GitConfig("github.token") || TokenSource
  .Environment("PACKAGE_GITHUB_TOKEN")
