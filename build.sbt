ThisBuild / scalaVersion        := "3.2.2"
ThisBuild / organization        := "com.fiatjaf"
ThisBuild / homepage            := Some(url("https://github.com/fiatjaf/snow"))
ThisBuild / licenses            += License.Apache2
ThisBuild / developers          := List(tlGitHubDev("fiatjaf", "fiatjaf"))

ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / tlSonatypeUseLegacyHost := false

Global / onChangedBuildSource := ReloadOnSourceChanges

val http4sVersion = "1.0.0-M36"

import org.openqa.selenium.WebDriver
// import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions
// import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.jsenv.selenium.SeleniumJSEnv

lazy val snow = crossProject(/*JVMPlatform,*/ JSPlatform /*, NativePlatform*/)
  .crossType(CrossType.Full)
  .in(file("."))
  .settings(
    name := "snow",
    description := "Scala Nostr W̶a̶r̶s̶h̶i̶p̶s̶ Utilities",
    libraryDependencies ++= Seq(
      "io.circe" %%% "circe-core" % "0.14.3",
      "io.circe" %%% "circe-generic" % "0.14.3",
      "io.circe" %%% "circe-parser" % "0.14.3",
      "com.fiatjaf" %%% "scoin" % "0.7.0",
      "org.http4s" %%% "http4s-core" % http4sVersion,
      "org.http4s" %%% "http4s-client" % http4sVersion,
      "org.http4s" %%% "http4s-dom" % http4sVersion,
      "com.lihaoyi" %%% "utest" % "0.8.0" % Test,
      "org.typelevel" %%% "cats-effect-testing-utest" % "1.5.0" % Test
    ),
    testFrameworks += new TestFramework("utest.runner.Framework"),
  )
  .jsEnablePlugins(ScalaJSImportMapPlugin)
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    jsEnv := {
      val options = new FirefoxOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options, SeleniumJSEnv.Config())
    },
    scalaJSImportMap := { (rawImport: String) =>
      if (
        rawImport.startsWith("@noble/hashes") ||
        rawImport.startsWith("@noble/hashes/hmac") ||
        rawImport.startsWith("@noble/secp256k1") ||
        rawImport.startsWith("@noble/secp256k1") ||
        rawImport.startsWith("@stablelib/chacha") ||
        rawImport.startsWith("@stablelib/chacha20poly1305")
      )
        "https://cdn.jsdelivr.net/npm/" + rawImport
      else
        rawImport
    }
  )

// maven magic, see https://github.com/makingthematrix/scala-suffix/tree/56270a#but-wait-thats-not-all
Compile / packageBin / packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "snow")
