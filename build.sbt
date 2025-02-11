ThisBuild / scalaVersion        := "3.3.4"
ThisBuild / organization        := "com.fiatjaf"
ThisBuild / homepage            := Some(url("https://github.com/fiatjaf/snow"))
ThisBuild / licenses            += License.Apache2
ThisBuild / developers          := List(tlGitHubDev("fiatjaf", "fiatjaf"))

ThisBuild / version := "0.0.2-SNAPSHOT"
ThisBuild / sonatypeCredentialHost := xerial.sbt.Sonatype.sonatypeLegacy

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val snow = project
  .in(file("."))
  .settings(
    name := "snow",
    description := "Scala Nostr W̶a̶r̶s̶h̶i̶p̶s̶ Utilities",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % "0.14.5",
      "io.circe" %%% "circe-generic" % "0.14.5",
      "io.circe" %%% "circe-parser" % "0.14.5",
      "com.fiatjaf" %%% "scoin" % "0.7.0",
      "org.http4s" %%% "http4s-dom" % "0.2.11",
      "org.http4s" %%% "http4s-client" % "0.23.25",

      "com.lihaoyi" %%% "utest" % "0.8.0" % Test
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) }
  ).enablePlugins(ScalaJSPlugin, EsbuildPlugin)
