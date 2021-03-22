package kinesis.mock.api

import cats.kernel.Eq
import io.circe._

import kinesis.mock.models.Tags

final case class ListTagsForStreamResponse(
    hasMoreTags: Boolean,
    tags: Tags
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
        tags <- x.downField("Tags").as[Tags]
      } yield ListTagsForStreamResponse(hasMoreTags, tags)

  implicit val listTagsForStreamResponseEq: Eq[ListTagsForStreamResponse] =
    Eq.fromUniversalEquals
}
