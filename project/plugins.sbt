addSbtPlugin("org.typelevel" % "sbt-typelevel-ci-release" % "0.4.13")

addSbtPlugin("org.portable-scala" % "sbt-scalajs-crossproject" % "1.2.0")

// For testing in browser. Must be placed before sbt-scalajs
// https://github.com/scala-js/scala-js-env-selenium

val http4sVersion = "0.23.18"

enablePlugins(BuildInfoPlugin)
buildInfoKeys += "http4sVersion" -> http4sVersion

libraryDependencies += "org.scala-js" %% "scalajs-env-selenium" % "1.1.1"
libraryDependencies += "org.http4s" %% "http4s-dsl" % http4sVersion
libraryDependencies += "org.http4s" %% "http4s-ember-server" % http4sVersion

addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.12.0")
addSbtPlugin("io.chrisdavenport" % "sbt-npm-dependencies" % "0.0.1")

addSbtPlugin("org.portable-scala" % "sbt-scala-native-crossproject" % "1.2.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.4.10")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.11.0")