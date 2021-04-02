package kinesis.mock.api

import cats.kernel.Eq
import io.circe._

import kinesis.mock.models.TagList

final case class ListTagsForStreamResponse(
    hasMoreTags: Boolean,
    tags: TagList
)

object ListTagsForStreamResponse {
  implicit val listTagsForStreamResponseCirceEncoder
      : Encoder[ListTagsForStreamResponse] =
    Encoder.forProduct2("HasMoreTags", "Tags")(x => (x.hasMoreTags, x.tags))

  implicit val listTagsForStreamResponseCirceDecoder
      : Decoder[ListTagsForStreamResponse] =
    x =>
      for {
        hasMoreTags <- x.downField("HasMoreTags").as[Boolean]
        tags <- x.downField("Tags").as[TagList]
      } yield ListTagsForStreamResponse(hasMoreTags, tags)

  implicit val listTagsForStreamResponseEq: Eq[ListTagsForStreamResponse] =
    Eq.fromUniversalEquals
}
