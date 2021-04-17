package kinesis.mock
package api

import cats.data.Validated._
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_AddTagsToStream.html
// https://docs.aws.amazon.com/streams/latest/dev/tagging.html
// https://docs.aws.amazon.com/directoryservice/latest/devguide/API_Tag.html
final case class AddTagsToStreamRequest(
    streamName: StreamName,
    tags: Tags
) {
  def addTagsToStream(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[Unit]] = streamsRef.get.flatMap { streams =>
    CommonValidations
      .validateStreamName(streamName)
      .andThen(_ =>
        CommonValidations
          .findStream(streamName, streams)
          .andThen(stream =>
            (
              CommonValidations.validateTagKeys(tags.tags.keys), {
                val valuesTooLong =
                  tags.tags.values.filter(x => x.length() > 255)
                if (valuesTooLong.nonEmpty)
                  InvalidArgumentException(
                    s"Values must be less than 255 characters. Invalid values: ${valuesTooLong.mkString(", ")}"
                  ).invalidNel
                else Valid(())
              }, {
                val invalidValues = tags.tags.values.filterNot(x =>
                  x.matches("^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-]*)$")
                )
                if (invalidValues.nonEmpty)
                  InvalidArgumentException(
                    s"Values contain invalid characters. Invalid values: ${invalidValues.mkString(", ")}"
                  ).invalidNel
                else Valid(())
              }, {
                val numberOfTags = tags.size
                if (numberOfTags > 10)
                  InvalidArgumentException(
                    s"Can only add 10 tags with a single request. Request contains $numberOfTags tags"
                  ).invalidNel
                else Valid(())
              }, {
                val totalTagsAfterAppend = (stream.tags |+| tags).size
                if (totalTagsAfterAppend > 50)
                  InvalidArgumentException(
                    s"AWS resources can only have 50 tags. Request would result in $totalTagsAfterAppend tags"
                  ).invalidNel
                else Valid(())
              }
            ).mapN((_, _, _, _, _) => stream)
          )
      )
      .traverse(stream =>
        streamsRef.update(x =>
          x.updateStream(stream.copy(tags = stream.tags |+| tags))
        )
      )
  }
}

object AddTagsToStreamRequest {
  implicit val addTagsToStreamRequestEncoder: Encoder[AddTagsToStreamRequest] =
    Encoder.forProduct2("StreamName", "Tags")(x => (x.streamName, x.tags))
  implicit val addTagsToStreamRequestDecoder
      : Decoder[AddTagsToStreamRequest] = { x =>
    for {
      streamName <- x.downField("StreamName").as[StreamName]
      tags <- x.downField("Tags").as[Tags]
    } yield AddTagsToStreamRequest(streamName, tags)
  }
  implicit val addTagsToStreamRequestEq: Eq[AddTagsToStreamRequest] =
    Eq.fromUniversalEquals
}
