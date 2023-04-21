package snow

import scala.concurrent.duration.*
import scoin.{Crypto, ByteVector32}
import scodec.bits.ByteVector
import fs2.{io => *, *}
import fs2.concurrent.{Topic, Channel}
import cats.implicits.*
import cats.effect.*
import org.http4s.Uri
import org.http4s.dom.*
import org.http4s.client.websocket.*
import io.circe.*
import io.circe.parser.decode
import io.circe.syntax.*

object Relay {
  def apply(uri: Uri): Resource[IO, Relay] = for {
          conn <- WebSocketClient[IO].connectHighLevel(WSRequest(uri))
          nextId <- Resource.eval(Ref[IO].of(0))
          commands <- Resource.eval(Channel.unbounded[IO,Json])
          events <- Resource.eval(Topic[IO, (String, Event)])
          eoses <- Resource.eval(Topic[IO,String])
          relay = new Relay(uri,conn,nextId,commands,events,eoses)
          //_ <- relay.start.background
      } yield relay
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
  ): Resource[IO,(List[Event], fs2.Stream[IO, Event])] = {
    Resource.eval(nextId.modify(x => (x + 1, x)).map(_.toString).flatMap { currId =>
      val send = commands.send(
        Seq("REQ".asJson, currId.asJson)
          .concat(filter.map(_.asJson))
          .asJson
      )

      val eose =
        eoses
          .subscribe(1)
          .collect {
            case subid if subid == currId => Event(kind = -1, content = "")
          }
          .delayBy(700.milliseconds)

      val receive =
        events
          .subscribe(1)
          .collect {
            case (subid, event) if subid == currId => event
          }
          .merge(eose)
          .evalTap(evt => IO.println(s"evt = $evt"))

      for {
        _ <- send
        compiled = receive.takeWhile(_.kind != -1).compile
        _ <- compiled.count.flatMap(i => IO.println(s"ccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccount is $i"))
        stored <- compiled.toList
        _ <- IO.println(stored.size)
        _ <- IO.println("***************************************************************************************************************************")
        live = receive.dropWhile(_.kind != -1).drop(1)
      } yield (stored, live)
    })
  }

  def start: IO[Unit] = {
    def receive =
      conn.receiveStream
        .collect { case WSFrame.Text(line, _) => line }
        .map(line => decode[List[Json]](line.toString))
        .collect { case Right(v) => v }
        .evalTap[IO, Unit] { msg =>
          msg match {
            case msg if msg.size == 2 && msg(0).as[String] == Right("EOSE") =>
              msg(1).as[String] match {
                case Right(subid) => eoses.publish1(subid).void
                case _            => IO.unit
              }
            case msg if msg.size == 3 && msg(0).as[String] == Right("EVENT") =>
              (msg(1).as[String], msg(2).as[Event]) match {
                case (Right(subid), Right(event)) if event.isValid =>
                  events.publish1((subid, event)).void
                case _ => IO.unit
              }
            case _ => IO.unit
          }
        }
        .evalTap { msg => IO.println(s"receive: $msg") }
        .compile
        .drain

    def send = commands.stream
      .evalTap { msg => conn.sendText(msg.noSpaces) }
      .evalTap { msg => IO.println(s"conn.sendText(${msg.noSpaces})") }
      .compile
      .drain

    (send, receive).parTupled.void
    IO.unit
  }
}