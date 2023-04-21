ThisBuild / scalaVersion        := "3.2.2"
ThisBuild / organization        := "com.fiatjaf"
ThisBuild / homepage            := Some(url("https://github.com/fiatjaf/snow"))
ThisBuild / licenses            += License.Apache2
ThisBuild / developers          := List(tlGitHubDev("fiatjaf", "fiatjaf"))

ThisBuild / version := "0.0.1-SNAPSHOT"
ThisBuild / tlSonatypeUseLegacyHost := false

Global / onChangedBuildSource := ReloadOnSourceChanges

lazy val fileServicePort = settingKey[Int]("Port for static file server")
Global / fileServicePort := {
  import cats.data.Kleisli
  import cats.effect.IO
  import cats.effect.unsafe.implicits.global
  import com.comcast.ip4s._
  import org.http4s._
  import org.http4s.dsl.io._
  import org.http4s.ember.server.EmberServerBuilder
  import org.http4s.server.staticcontent._
  import java.net.InetSocketAddress

  (for {
    deferredPort <- IO.deferred[Int]
    _ <- EmberServerBuilder
      .default[IO]
      .withPort(port"0")
      .withHttpWebSocketApp { wsb =>
        HttpRoutes
          .of[IO] {
            case Method.GET -> Root / "ws" =>
              wsb.build(identity)
            case req =>
              fileService[IO](FileService.Config(".")).orNotFound.run(req).map { res =>
                // TODO find out why mime type is not auto-inferred
                if (req.uri.renderString.endsWith(".js"))
                  res.withHeaders(
                    "Service-Worker-Allowed" -> "/",
                    "Content-Type" -> "text/javascript"
                  )
                else res
              }
          }
          .orNotFound
      }
      .build
      .map(_.address.getPort)
      .evalTap(deferredPort.complete(_))
      .useForever
      .start
    port <- deferredPort.get
  } yield port).unsafeRunSync()
}

val http4sVersion = "1.0.0-M36"

import org.openqa.selenium.WebDriver
// import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.firefox.FirefoxOptions
// import org.scalajs.jsenv.nodejs.NodeJSEnv
import org.scalajs.jsenv.selenium.SeleniumJSEnv

lazy val seleniumConfig = Def.setting {
  SeleniumJSEnv
    .Config()
    .withMaterializeInServer(
      "target/selenium",
      s"http://localhost:${fileServicePort.value}/target/selenium/")
}

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
  .enablePlugins(ScalaJSImportMapPlugin)
  .jsSettings(
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
    jsEnv := {
      val options = new FirefoxOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options, seleniumConfig.value)
    },
    scalaJSImportMap := { (rawImport: String) =>
      if (
        rawImport.startsWith("@noble/hashes") ||
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

lazy val testsFirefox = project
  .in(file(".testsFirefox"))
  .dependsOn(snow.js)
  .aggregate(snow.js)
  .settings(
    jsEnv := {
      val options = new FirefoxOptions()
      options.setHeadless(true)
      new SeleniumJSEnv(options, seleniumConfig.value)
    }
  )

// maven magic, see https://github.com/makingthematrix/scala-suffix/tree/56270a#but-wait-thats-not-all
Compile / packageBin / packageOptions += Package.ManifestAttributes("Automatic-Module-Name" -> "snow")
