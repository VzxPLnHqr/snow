package snow

import utest.*
import scala.concurrent.duration.*
import scala.scalajs
import cats.implicits.*
import cats.effect.*
import cats.effect.unsafe.implicits.global
import org.http4s.syntax.literals.uri
import fs2.concurrent.Channel
import cats.effect.testing.utest.EffectTestSuite

object RelayTest extends EffectTestSuite[IO] {
  scalajs.js.Dynamic.global.globalThis.require("websocket-polyfill")

  val tests = Tests {
    test("connect to relay and subscribe") {
      Relay(uri"wss://relay.damus.io/").use { relay =>
        relay
          .subscribe(
            Filter(kinds = List(1), limit = Some(5))
          )
          .flatMap { (stored, live) =>
            IO.delay {
              assert(stored.size == 5)
            } *>
              live
                .evalTap { evt =>
                  IO.delay {
                    assert(evt.kind == 0)
                  }
                }
                .take(1)
                .compile
                .drain
          }
      }
    }
  }
}
