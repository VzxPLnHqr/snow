package snow

import io.circe.*
import io.circe.syntax.*
import io.circe.Decoder.Result
import cats.syntax.all.*

object Messages:
  object FromRelay:

    case class OK(eventId: String, accepted: Boolean, message: String)

    given Decoder[OK] = new Decoder[OK]:
      def apply(c: HCursor): Result[OK] =
        (
          // Check that the first element equals "OK"
          c.downN(0).as[String].flatMap{ tag => 
              if tag == "OK" then Right(()) 
               else Left(DecodingFailure(s"Expected tag 'OK' but found '$tag'", c.history))
          },
          // Decode the remaining elements by their array indices
          c.downN(1).as[String],
          c.downN(2).as[Boolean],
          c.downN(3).as[String]
        ).mapN{
          case (_, eventId, accepted, message) => OK(eventId, accepted, message)
        }