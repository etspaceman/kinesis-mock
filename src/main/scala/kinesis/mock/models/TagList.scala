package kinesis.mock.models

import cats.Eq
import io.circe._

final case class TagList(tags: List[TagListEntry])

object TagList {
  implicit val tagListEq: Eq[TagList] = Eq.fromUniversalEquals
  implicit val tagListCirceEncoder: Encoder[TagList] =
    Encoder.encodeList[TagListEntry].contramap(_.tags)
  implicit val tagListCirceDecoder: Decoder[TagList] =
    Decoder[List[TagListEntry]].map(TagList.apply)
  def fromTags(tags: Tags): TagList = TagList(
    tags.tags.toList.map { case (key, value) => TagListEntry(key, value) }
  )
}
