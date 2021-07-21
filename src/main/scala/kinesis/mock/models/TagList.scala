package kinesis.mock.models

import cats.Eq
import io.circe._

final case class TagList(tags: Vector[TagListEntry])

object TagList {
  implicit val tagListEq: Eq[TagList] = Eq.fromUniversalEquals
  implicit val tagListCirceEncoder: Encoder[TagList] =
    Encoder.encodeVector[TagListEntry].contramap(_.tags)
  implicit val tagListCirceDecoder: Decoder[TagList] =
    Decoder[Vector[TagListEntry]].map(TagList.apply)
  def fromTags(tags: Tags): TagList = TagList(
    tags.tags.toVector.map { case (key, value) => TagListEntry(key, value) }
  )
}
