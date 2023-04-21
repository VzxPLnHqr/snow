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

import org.http4s.Uri
//import org.http4s.client.websocket.*
import org.http4s.dom.WebSocketClient
import org.http4s.client.websocket.WSRequest

object RelayTest extends EffectTestSuite[IO] {
  //scalajs.js.Dynamic.global.globalThis.require("websocket-polyfill")
  //scalajs.js.Dynamic.global.globalThis.require("websocket")
  
  val tests = Tests {
    test("basic websocket test") {
      WebSocketClient[IO].connectHighLevel(WSRequest(uri"wss://relay.damus.io/")).use(_ => IO.unit)
        /*.use { conn => 
          //conn.sendText(""""["REQ","0",{"kinds":[1],"limit":5}]""")
          //>> conn.receiveStream.take(1).compile.toList
          IO.unit
        }*/
    }
    /*test("create relay") {
      Relay(uri"wss://relay.damus.io/").use{
        relay => IO.println("relay created")
      }
    }*/
    /*test("connect to relay and subscribe") {
      Relay(uri"wss://relay.damus.io/").flatMap { relay =>
        relay
          .subscribe(
            Filter(kinds = List(1), limit = Some(5))
          )
      }
      .use { (stored, live) => 
        IO.delay {
          assert(stored.size == 5)
        } /**>
          live
            .evalTap { evt =>
              IO.delay {
                assert(evt.kind == 0)
              }
            }
            .take(1)
            .compile
            .drain*/
      }
    }*/
  }
}
