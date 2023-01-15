package snow

import utest._
import scala.concurrent.duration._
import scala.scalajs
import cats.implicits._
import cats.effect._
import cats.effect.unsafe.implicits.global
import org.http4s.syntax.literals.uri
import fs2.concurrent.Channel

object RelayTest extends TestSuite {
  scalajs.js.Dynamic.global.globalThis.require("websocket-polyfill")

  val tests = Tests {
    test("connect to relay") {
      val program = Relay(uri"wss://nostr.fmt.wiz.biz").use { relay =>
        for {
          sub <- relay.subscribe(
            Filter(kinds = List(0), since = Some(1673626539))
          )
          _ <- sub.evalTap { evt => IO.println(evt) }.compile.drain
        } yield ()
      }

      program.unsafeToFuture()
    }
  }
}
