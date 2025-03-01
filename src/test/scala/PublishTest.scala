package snow

import cats.effect.*
import cats.effect.unsafe.implicits.global
import cats.implicits.*
import org.http4s.syntax.literals.uri
import utest.*
import scoin.PrivateKey
import scodec.bits.ByteVector
import scala.concurrent.duration.*
import io.circe.*
import io.circe.syntax.*

object PublishTest extends TestSuite:
  val tests = Tests:
    /**
      * to run this test locally, you need to have a relay running:
      * `$ nix run nixpkgs#nak -- serve`
      * will create a relay running at ws://localhost:10547
      */

    /*test("connect to local relay"):
      Relay.mkResourceForIO(uri"ws://localhost:10547")
        .flatMap(_.subscribe(Filter())).use {
          case (storedEvents, _) => IO.println(storedEvents.size)
        }
        .unsafeToFuture()
    */

    test("publish signed event"):
      val event = Event(1, "hello hello, sorry, just testing")
      val signedEvent = event.sign(
        PrivateKey(
          ByteVector.fromValidHex(
            "7708c95f09947507c1044e8f48bcf6350aa6bff1507dd4acfc755b9239b5c962"
          )
        )
      )
      //val relayUri = uri"ws://localhost:10547"
      val relayUri = uri"wss://relay.damus.io"
      val program = Relay.mkResourceForIO(relayUri).use{
        relay =>
          IO.println(s"event: ${signedEvent.asJson.printWith(Printer.noSpaces)}") 
          *> IO.println(s"submitting without signature")
            *> relay.submitEvent(signedEvent.copy(sig = None), assumeValid = true).reject{
              case Messages.FromRelay.OK(_,accepted,message) 
                if !accepted => new RuntimeException(message)
            }.attempt.map(_.isLeft)
          *> IO.println(s"submitting with signature")
            *> relay.submitEvent(signedEvent).map(_.accepted).attempt.map(_.isRight)
            // now we lookup the event from the relay
            *> relay.lookupEventById(signedEvent.id.get).map{
              case Some(evt) => assert(evt.content == signedEvent.content)
              case None => throw RuntimeException("no event found")
            }
            // also make sure that events which do not exist return none
            *> relay.lookupEventById("ab"*32).map(_.isEmpty).map(assert(_))
      }
      program.unsafeToFuture()