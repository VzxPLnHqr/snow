package snow

import utest.*
import scala.concurrent.duration.*
import scala.scalajs
import cats.implicits.*
import cats.effect.*
import cats.effect.unsafe.implicits.global
import org.http4s.syntax.literals.uri
import fs2.concurrent.Channel

object RelayTest extends TestSuite {
  val tests = Tests {
    test("connect to relay and subscribe") {
      val numStoredEvents = 3
      val program = Relay.mkResourceForIO(uri"wss://relay.damus.io", debugOn = true)
        .use { relay => 
          relay
            .subscribe(
              Filter(kinds = List(1), limit = Some(numStoredEvents))
            )
            .flatMap { (stored, live) =>
              stored.traverse(e => IO.println((e.kind, e.hash))) *> IO.println(s"done processing ${stored.size} stored events") *>
              IO.delay {
                assert(stored.size == numStoredEvents)
              } *> IO.println("now processing live stream of events (stopping after 5)") *>
                live
                  .take(5)
                  .evalTap(e => IO.println((e.kind,e.hash)))
                  .compile
                  .drain
            }
        }

      program.unsafeToFuture()
    }
  }
}
