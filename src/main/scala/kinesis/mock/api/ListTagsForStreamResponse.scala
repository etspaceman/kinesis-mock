package kinesis.mock.api

import io.circe._

final case class ListTagsForStreamResponse(
    hasMoreTags: Boolean,
    tags: Map[String, String]
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
        tags <- x.downField("Tags").as[Map[String, String]]
      } yield ListTagsForStreamResponse(hasMoreTags, tags)
}
