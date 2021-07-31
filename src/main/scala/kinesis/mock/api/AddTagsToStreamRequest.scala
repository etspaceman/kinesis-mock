package kinesis.mock
package api

import cats.Eq
import cats.effect.IO
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations
import cats.effect.Ref

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_AddTagsToStream.html
// https://docs.aws.amazon.com/streams/latest/dev/tagging.html
// https://docs.aws.amazon.com/directoryservice/latest/devguide/API_Tag.html
final case class AddTagsToStreamRequest(
    streamName: StreamName,
    tags: Tags
) {
  def addTagsToStream(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] = streamsRef.modify { streams =>
    CommonValidations
      .validateStreamName(streamName)
      .flatMap(_ =>
        CommonValidations
          .findStream(streamName, streams)
          .flatMap(stream =>
            (
              CommonValidations.validateTagKeys(tags.tags.keys), {
                val valuesTooLong =
                  tags.tags.values.filter(x => x.length() > 256)
                if (valuesTooLong.nonEmpty)
                  InvalidArgumentException(
                    s"Values must be less than 256 characters. Invalid values: ${valuesTooLong.mkString(", ")}"
                  ).asLeft
                else Right(())
              }, {
                val invalidValues = tags.tags.values.filterNot(x =>
                  x.matches("^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-]*)$")
                )
                if (invalidValues.nonEmpty)
                  InvalidArgumentException(
                    s"Values contain invalid characters. Invalid values: ${invalidValues.mkString(", ")}"
                  ).asLeft
                else Right(())
              }, {
                val numberOfTags = tags.size
                if (numberOfTags > 10)
                  InvalidArgumentException(
                    s"Can only add 10 tags with a single request. Request contains $numberOfTags tags"
                  ).asLeft
                else Right(())
              }, {
                val totalTagsAfterAppend = (stream.tags |+| tags).size
                if (totalTagsAfterAppend > 50)
                  InvalidArgumentException(
                    s"AWS resources can only have 50 tags. Request would result in $totalTagsAfterAppend tags"
                  ).asLeft
                else Right(())
              }
            ).mapN((_, _, _, _, _) => stream)
          )
      )
      .map(stream =>
        (streams.updateStream(stream.copy(tags = stream.tags |+| tags)), ())
      )
      .sequenceWithDefault(streams)
  }
}

object AddTagsToStreamRequest {
  implicit val addTagsToStreamRequestCirceEncoder
      : circe.Encoder[AddTagsToStreamRequest] =
    circe.Encoder.forProduct2("StreamName", "Tags")(x => (x.streamName, x.tags))
  implicit val addTagsToStreamRequestCirceDecoder
      : circe.Decoder[AddTagsToStreamRequest] = { x =>
    for {
      streamName <- x.downField("StreamName").as[StreamName]
      tags <- x.downField("Tags").as[Tags]
    } yield AddTagsToStreamRequest(streamName, tags)
  }
  implicit val addTagsToStreamRequestEncoder: Encoder[AddTagsToStreamRequest] =
    Encoder.derive
  implicit val addTagsToStreamRequestDecoder: Decoder[AddTagsToStreamRequest] =
    Decoder.derive
  implicit val addTagsToStreamRequestEq: Eq[AddTagsToStreamRequest] =
    Eq.fromUniversalEquals
}
