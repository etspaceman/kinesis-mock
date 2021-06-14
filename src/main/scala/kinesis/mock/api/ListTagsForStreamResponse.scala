package kinesis.mock
package api

import cats.kernel.Eq
import io.circe

import kinesis.mock.models.TagList

final case class ListTagsForStreamResponse(
    hasMoreTags: Boolean,
    tags: TagList
)

object ListTagsForStreamResponse {
  implicit val listTagsForStreamResponseCirceEncoder
      : circe.Encoder[ListTagsForStreamResponse] =
    circe.Encoder.forProduct2("HasMoreTags", "Tags")(x =>
      (x.hasMoreTags, x.tags)
    )

  implicit val listTagsForStreamResponseCirceDecoder
      : circe.Decoder[ListTagsForStreamResponse] =
    x =>
      for {
        hasMoreTags <- x.downField("HasMoreTags").as[Boolean]
        tags <- x.downField("Tags").as[TagList]
      } yield ListTagsForStreamResponse(hasMoreTags, tags)

  implicit val listTagsForStreamResponseEncoder
      : Encoder[ListTagsForStreamResponse] = Encoder.derive
  implicit val listTagsForStreamResponseDecoder
      : Decoder[ListTagsForStreamResponse] = Decoder.derive

  implicit val listTagsForStreamResponseEq: Eq[ListTagsForStreamResponse] =
    Eq.fromUniversalEquals
}
