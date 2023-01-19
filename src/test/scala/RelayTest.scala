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
    test("connect to relay and subscribe") {
      val program = Relay(uri"wss://nostr.fmt.wiz.biz").use { 
        relay => for {
          _ <- relay.start
          subscription <- relay.subscribe(
            Filter(kinds = List(1), limit = Some(5))
          )
          stored = subscription._1
          live = subscription._2
          _ <- IO.println("****************************************")
          _ <- IO.println(stored.size)
          _ <- IO(assert(stored.size == 5))
          _ <- IO.println("why is this line not printing when the test runs?")
          /*.flatMap { (stored, live) =>
            IO.println(stored.size) *>
            IO.delay(assert(stored.size == 5)) *>
            IO.println("*****************************")
            *>
              live
                .evalTap { evt =>
                  IO.delay {
                    assert(evt.kind == 0)
                  }
                }
                .take(1)
                .compile
                .drain
          }*/
        } yield ()
      }

      program.unsafeRunAsync{
        case Left(e) => require(false, e.getMessage())
        case Right(value) => assert(true)
      }
    }
  }
}
