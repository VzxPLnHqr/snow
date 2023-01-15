package snow

import scala.concurrent.duration._
import scodec.bits.ByteVector
import scoin.{Crypto, ByteVector32}
import fs2.{io => _, _}
import fs2.concurrent.{Topic, Channel}
import cats.implicits._
import cats.effect._
import org.http4s.Uri
import org.http4s.dom._
import org.http4s.client.websocket._
import io.circe._
import io.circe.parser.decode
import io.circe.syntax._

object Relay {
  def apply(uri: Uri): Resource[IO, Relay] = {
    val normalized = uri
      .withPath(uri.path.dropEndsWithSlash)
      .withoutFragment
      .toString()

    (
      Resource.eval(Ref[IO].of(0)),
      Resource.eval(Channel.unbounded[IO, Json]),
      Resource.eval(Topic[IO, (String, Event)]),
      Resource.eval(Topic[IO, String]),
      WebSocketClient[IO].connectHighLevel(WSRequest(uri))
    ).mapN((nextId, commands, events, eoses, conn) =>
      new Relay(uri, conn, nextId, commands, events, eoses)
    ).evalTap(_.start)
  }
}

class Relay(
    uri: Uri,
    conn: WSConnectionHighLevel[IO],
    nextId: Ref[IO, Int],
    commands: Channel[IO, Json],
    events: Topic[IO, (String, Event)],
    eoses: Topic[IO, String]
) {
  def subscribe(
      filter: Filter*
  ): IO[fs2.Stream[IO, Event]] = {
    val subid = nextId.modify(x => (x + 1, x)).map(_.toString)

    val send = for {
      id <- subid
      res <- commands.send(
        Seq("REQ".asJson, id.asJson)
          .concat(filter.map(_.asJson))
          .asJson
      )
    } yield ()

    val receive = subid.map { id =>
      events
        .subscribe(1)
        .collect {
          case (subid, event) if subid == id => event
        }
    }

    (receive, send.delayBy(500.milliseconds)).parTupled.map {
      case (events, _) =>
        events
    }
  }

  def start: IO[Unit] = {
    val receive =
      conn.receiveStream
        .collect { case WSFrame.Text(line, _) => line }
        .map(line => decode[List[Json]](line.toString))
        .collect { case Right(v) => v }
        .evalMap[IO, Unit] { msg =>
          msg match {
            case msg if msg.size == 2 && msg(0).as[String] == Right("EOSE") =>
              msg(1).as[String] match {
                case Right(subid) => eoses.publish1(subid) *> IO.unit
                case _            => IO.unit
              }
            case msg if msg.size == 3 && msg(0).as[String] == Right("EVENT") =>
              (msg(1).as[String], msg(2).as[Event]) match {
                case (Right(subid), Right(event)) if event.isValid =>
                  events.publish1((subid, event)) *> IO.unit
                case _ => IO.unit
              }
            case _ => IO.unit
          }
        }
        .compile
        .drain

    val send = commands.stream
      .evalTap { msg => conn.sendText(msg.noSpaces) }
      .compile
      .drain

    (send, receive).parTupled *> IO.unit
  }
}
