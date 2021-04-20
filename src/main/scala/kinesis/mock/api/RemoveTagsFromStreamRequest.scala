package kinesis.mock
package api

import cats.data.Validated._
import cats.effect.IO
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations
import cats.effect.Ref

final case class RemoveTagsFromStreamRequest(
    streamName: StreamName,
    tagKeys: List[String]
) {
  // https://docs.aws.amazon.com/kinesis/latest/APIReference/API_RemoveTagsFromStream.html
  // https://docs.aws.amazon.com/streams/latest/dev/tagging.html
  // https://docs.aws.amazon.com/directoryservice/latest/devguide/API_Tag.html
  def removeTagsFromStream(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[Unit]] = streamsRef.get.flatMap(streams =>
    CommonValidations
      .validateStreamName(streamName)
      .andThen(_ =>
        CommonValidations
          .findStream(streamName, streams)
          .andThen(stream =>
            (
              CommonValidations.validateTagKeys(tagKeys), {
                val numberOfTags = tagKeys.length
                if (numberOfTags > 10)
                  InvalidArgumentException(
                    s"Can only remove 50 tags with a single request. Request contains $numberOfTags tags"
                  ).invalidNel
                else Valid(())
              }
            ).mapN((_, _) => stream)
          )
      )
      .traverse(stream =>
        streamsRef.update(x =>
          x.updateStream(stream.copy(tags = stream.tags -- tagKeys))
        )
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
      streamName <- x.downField("StreamName").as[StreamName]
      tagKeys <- x.downField("TagKeys").as[List[String]]
    } yield RemoveTagsFromStreamRequest(streamName, tagKeys)
  }
  implicit val removeTagsFromStreamRequestEq: Eq[RemoveTagsFromStreamRequest] =
    Eq.fromUniversalEquals

}
