package snow

import cats.effect.IO
import cats.effect.testing.utest.EffectTestSuite
import utest.*

import org.http4s.Uri
import org.http4s.syntax.literals.uri
import org.http4s.dom.WebSocketClient
import org.http4s.client.websocket.WSFrame
import org.http4s.client.websocket.WSRequest

import scodec.bits.ByteVector


object WebsocketTest extends EffectTestSuite[IO] {
//  @scalajs.js.native
//  @scalajs.js.annotation.JSGlobal
//  val WebSocket = scalajs.js.Dynamic.global.globalThis.require("ws")
  val tests = Tests {
    test("basic websocket client sending/receiving frames") {
      //taken mostly from https://github.com/http4s/http4s-dom/blob/series/0.2/testsBrowser/src/test/scala/org/http4s/dom/WebSocketSuite.scala
      WebSocketClient[IO]
      .connectHighLevel(
        WSRequest(uri"wss://relay.damus.io/"))
      .use { conn =>
        for {
          _ <- conn.sendText(""""["REQ","0",{"kinds":[1],"limit":5}]""")
          recv <- conn.receiveStream.take(5).compile.toList
        } yield recv
      }.map(evts => assert(evts.size == 5))
    }
  }
}