package snow

import cats.Show
import cats.effect.*
import cats.implicits.*
import fs2.concurrent.Channel
import fs2.concurrent.Topic
import fs2.{io as _, *}
import io.circe.*
import io.circe.parser.decode
import io.circe.syntax.*
import org.http4s.Uri
import org.http4s.client.websocket.*
import org.http4s.dom.*
import snow.Messages.FromRelay.OK
import scala.concurrent.duration.*

/** An implementation of Relay[F] provides methods like `subscribe` and access
  * to the underlying streams of things while hiding the details about how it
  * gets the things.
  */
trait Relay[F[_]]:
  def uri: Uri
  def nextId: Ref[F, Int]
  def commands: Channel[F, Json]
  def events: Topic[F, (String, Event)]

  /** subscribing with a `Filter` will give us a list of stored events and a
    * stream of future events
    */
  def subscribe(filter: Filter*): Resource[F, (List[Event], Stream[F, Event])]

  /**
    * Send `event` to the relay and wait for an "OK" from the relay. It is
    * up to the caller to process the OK and determine if the event was
    * accepted or not and to take any corrective action and resubmit if necessary.
    * See [nip01](https://github.com/nostr-protocol/nips/blob/master/01.md)
    */
  def submitEvent(event: Event, assumeValid: Boolean = false): F[Messages.FromRelay.OK]

  /**
   * Given an `eventId` ask the relay for the corresponding `Event` or timeout
   * and return `None`
   */
  def lookupEventById(eventId: String, timeout: FiniteDuration = 5.seconds): F[Option[Event]]

object Relay {

  /* this should ideally be for a generic F[_], but we hard code it to F = IO */
  def apply(uri: Uri): Resource[IO, Relay[IO]] = mkResourceForIO(uri)

  /* can make this more generic eventually, but for now it is tied to F = IO */
  def mkResourceForIO(
      uri: Uri,
      debugOn: Boolean = false
  ): Resource[IO, Relay[IO]] =

    def debug[A](x: A)(using Show[A]) =
      if (debugOn) then IO.println(x) else IO.unit

    for
      nextId <- Ref[IO].of(0).toResource
      commands <- Channel.unbounded[IO, Json].toResource
      events <- Topic[IO, (String, Event)].toResource
      oks <- Topic[IO, Messages.FromRelay.OK].toResource
      conn <- WebSocketClient[IO].connectHighLevel(WSRequest(uri))

      // here we weave together the websocket streams and start the background
      // process that keeps them going
      background <- {
        val receive = debug("opening receive stream") *> conn.receiveStream
          .collect { case WSFrame.Text(line, _) => line }
          .map(line => decode[List[Json]](line.toString))
          .collect { case Right(v) => v }
          .evalTap[IO, Unit] { msg =>
            msg match {
              case msg if msg.size == 2 && msg(0).as[String] == Right("EOSE") =>
                msg(1).as[String] match {
                  case Right(subid) =>
                    events
                      .publish1((subid, Event(kind = -1, content = "")))
                      .void
                      *> debug(s"$subid: eose")
                  case _ => IO.unit
                }
              case msg
                if msg.size == 3 && msg(0).as[String] == Right("EVENT") =>
                  (msg(1).as[String], msg(2).as[Event]) match {
                    case (Right(subid), Right(event)) if event.isValid =>
                      debug(s"$subid: ${event.hash}")
                        *> events.publish1((subid, event)).void
                    case _ => IO.unit
                  }
              case msg if msg.asJson.as[Messages.FromRelay.OK].isRight =>
                msg.asJson.as[Messages.FromRelay.OK] match
                  case Left(_) => IO.raiseError(new RuntimeException("impossible!"))
                  case Right(ok) => oks.publish1(ok).void

              case msg => debug(s"unable to decode: ${msg}") *> IO.unit
            }
          }
          .compile
          .drain

        val send = debug("opening send stream") *> commands.stream
          .evalMap { msg =>
            debug(s"sending request: $msg") *> conn.sendText(msg.noSpaces)
          }
          .compile
          .drain

        (send, receive).parTupled.void.background
      }
    // only thing left to do now is return our Relay
    yield new RelayImplForIO(uri, nextId, commands, events, oks, debugOn)
}

class RelayImplForIO(
    val uri: Uri,
    val nextId: Ref[IO, Int],
    val commands: Channel[IO, Json],
    val events: Topic[IO, (String, Event)],
    val oks: Topic[IO, Messages.FromRelay.OK],
    debugOn: Boolean
) extends Relay[IO] {

  def debug[A](x: A)(using Show[A]) =
    if (debugOn) then IO.println(x.show) else IO.unit

  def submitEvent(event: Event, assumeValid: Boolean = false): IO[Messages.FromRelay.OK] =
    val checkValid = 
      if !assumeValid then
        IO.raiseUnless(event.isValid)(RuntimeException("invalid: did you forget to sign?"))
      else 
        IO.unit
      
    val send = commands.send(
      Seq("EVENT".asJson, event.asJson).asJson
    )

    val listen = oks.subscribe(1).collect {
        case ok @ Messages.FromRelay.OK(eventId, accepted, message)
          // if an event is submitted such that the id cannot be correctly
          // calculated (maybe it is missing pubkey), then relays 
          // may return a "" for the eventId so here we go ahead and pass that 
          // through to the caller. The risk is that in a highly concurrent 
          // environment, this particular OK message might pertain to a different 
          // event all together. Regardless, the caller has not yet confirmed
          // that the event has been accepted, so it should take efforts to remedy.
          if(eventId == event.hash.toHex || eventId.isEmpty) => ok
      }
      .head
      .compile
      .onlyOrError

    listen <& (checkValid *> send)

  def lookupEventById(eventId: String, timeout: FiniteDuration): IO[Option[Event]] =
    subscribe(Filter(ids = List(eventId), limit = Some(1))).use {
      case (storedEvents, liveEvents) => 
        val allEvents = Stream(storedEvents*).covary[IO] ++ liveEvents
        allEvents.head.compile.onlyOrError.timeout(timeout).option
    }

  def subscribe(
      filter: Filter*
  ): Resource[IO, (List[Event], fs2.Stream[IO, Event])] = {
    nextId.getAndUpdate(_ + 1).map(_.toString).toResource.flatMap { currId =>
      val send = commands.send(
        Seq("REQ".asJson, currId.asJson)
          .concat(filter.map(_.asJson))
          .asJson
      )

      val receive =
        events
          .subscribe(1)
          .collect {
            case (subid, event) if subid == currId => event
          }

      // we make sure to trigger `send` first
      send.background *> splitHistorical(receive)
    }
  }

  /** split into historical versus live events, where an event of kind -1
    * designates the marker between past and present from SystemFw's help here:
    * https://discord.com/channels/632277896739946517/632310980449402880/1337198474252255264
    */
  private def splitHistorical(
      in: Stream[IO, Event]
  ): Resource[IO, (List[Event], Stream[IO, Event])] =
    (
      Deferred[IO, List[Event]].toResource,
      Channel.unbounded[IO, Event].toResource
    )
      .flatMapN { (historical, live) =>
        def split(
            in: Stream[IO, Event],
            acc: Chunk[Event] = Chunk.empty
        ): Pull[IO, Event, Unit] =
          in.pull.uncons1 // ideally done with uncons + chunk split, left for the reader
            .flatMap {
              case None => Pull.done
              case Some((n, rest)) =>
                if n.kind == -1
                then
                  Pull.eval(historical.complete(acc.toList)) >> rest.pull.echo
                else split(rest, acc ++ Chunk(n))
            }

        split(in).stream
          .through(live.sendAll)
          .compile
          .drain
          .background
          .evalMap { _ =>
            historical.get.tupleRight(live.stream)
          }
      }
}
