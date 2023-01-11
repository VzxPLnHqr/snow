package snow

import scala.util.chaining._
import io.circe._
import io.circe.syntax._

object Filter {
  given Decoder[Filter] = new Decoder[Filter] {
    final def apply(c: HCursor): Decoder.Result[Filter] = {
      // tag fields
      val tags =
        c.keys
          .map(_.filter(_.startsWith("#")).flatMap { key =>
            c.downField(key).as[List[String]] match {
              case Right(v) => Some((key.drop(1), v))
              case Left(_)  => None
            }
          }.toMap)
          .getOrElse(Map.empty)

      Right(
        Filter(
          authors =
            c.downField("authors").as[List[String]].getOrElse(List.empty),
          kinds = c.downField("kinds").as[List[Int]].getOrElse(List.empty),
          ids = c.downField("ids").as[List[String]].getOrElse(List.empty),
          tags = tags,
          since = c.downField("since").as[Long].toOption,
          until = c.downField("until").as[Long].toOption,
          limit = c.downField("limit").as[Int].toOption
        )
      )
    }
  }
  given Encoder[Filter] = new Encoder[Filter] {
    final def apply(f: Filter): Json = {
      var fj = JsonObject(
        "ids" := f.ids,
        "authors" := f.authors,
        "kinds" := f.kinds,
        "since" := f.since,
        "until" := f.until,
        "limit" := f.limit
      )

      f.tags.foreachEntry { (k, v) =>
        fj = fj.add(s"#${k}", v.asJson)
      }

      fj.asJson
    }
  }
}

case class Filter(
    ids: List[String] = List.empty,
    authors: List[String] = List.empty,
    kinds: List[Int] = List.empty,
    tags: Map[String, List[String]] = Map.empty,
    since: Option[Long] = None,
    until: Option[Long] = None,
    limit: Option[Int] = None
) {
  def matches(event: Event): Boolean =
    (ids.isEmpty || ids.contains(event.id)) &&
      (kinds.isEmpty || kinds.contains(event.kind)) &&
      (authors.isEmpty || authors.contains(event.pubkey)) &&
      tags
        .map { case ((tag, vals)) =>
          event
            .getTagValues(tag)
            .exists(tagValue => vals.contains(tagValue))
        }
        .forall(_ == true) &&
      since.map(event.created_at > _).getOrElse(true) &&
      until.map(event.created_at < _).getOrElse(true)
}
