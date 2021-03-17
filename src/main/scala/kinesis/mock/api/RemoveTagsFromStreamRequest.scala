package kinesis.mock
package api

import cats.data.Validated._
import cats.data._
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

final case class RemoveTagsFromStreamRequest(
    streamName: String,
    tagKeys: List[String]
) {
  // https://docs.aws.amazon.com/kinesis/latest/APIReference/API_RemoveTagsFromStream.html
  // https://docs.aws.amazon.com/streams/latest/dev/tagging.html
  // https://docs.aws.amazon.com/directoryservice/latest/devguide/API_Tag.html
  def removeTagsFromStream(
      streams: Streams
  ): ValidatedNel[KinesisMockException, Streams] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen(stream =>
        (
          CommonValidations.validateStreamName(streamName),
          CommonValidations.validateTagKeys(tagKeys), {
            val numberOfTags = tagKeys.length
            if (numberOfTags > 10)
              InvalidArgumentException(
                s"Can only remove 50 tags with a single request. Request contains $numberOfTags tags"
              ).invalidNel
            else Valid(())
          }
        ).mapN((_, _, _) =>
          streams.updateStream(stream.copy(tags = stream.tags -- tagKeys))
        )
      )
}

object RemoveTagsFromStreamRequest {
  implicit val removeTagsFromStreamRequestEncoder
      : Encoder[RemoveTagsFromStreamRequest] =
    Encoder.forProduct2("StreamName", "TagKeys")(x => (x.streamName, x.tagKeys))
  implicit val removeTagsFromStreamRequestDecoder
      : Decoder[RemoveTagsFromStreamRequest] = { x =>
    for {
      streamName <- x.downField("StreamName").as[String]
      tagKeys <- x.downField("TagKeys").as[List[String]]
    } yield RemoveTagsFromStreamRequest(streamName, tagKeys)
  }
  implicit val removeTagsFromStreamRequestEq: Eq[RemoveTagsFromStreamRequest] =
    Eq.fromUniversalEquals

}
