package kinesis.mock
package api

import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

final case class AddTagsToStreamRequest(
    streamName: String,
    tags: Map[String, String]
)

object AddTagsToStreamRequest {
  implicit val addTagsToStreamRequestEncoder: Encoder[AddTagsToStreamRequest] =
    Encoder.forProduct2("StreamName", "Tags")(x => (x.streamName, x.tags))
  implicit val addTagsToStreamRequestDecoder
      : Decoder[AddTagsToStreamRequest] = { x =>
    for {
      streamName <- x.downField("StreamName").as[String]
      tags <- x.downField("Tags").as[Map[String, String]]
    } yield AddTagsToStreamRequest(streamName, tags)
  }

  // https://docs.aws.amazon.com/kinesis/latest/APIReference/API_AddTagsToStream.html
  // https://docs.aws.amazon.com/streams/latest/dev/tagging.html
  // https://docs.aws.amazon.com/directoryservice/latest/devguide/API_Tag.html
  def addTagsToStream(
      req: AddTagsToStreamRequest,
      streams: Streams
  ): Either[KinesisMockException, Streams] =
    for {
      _ <- CommonValidations.validateStreamName(req.streamName)
      stream <- Either.fromOption(
        streams.streams.find(_.name == req.streamName),
        ResourceNotFoundException(s"Stream name ${req.streamName} not found")
      )
      _ <- CommonValidations.validateTagKeys(req.tags.keys)
      _ <- {
        val valuesTooLong = req.tags.values.filter(x => x.length() > 255)
        if (valuesTooLong.nonEmpty)
          Left(
            InvalidArgumentException(
              s"Values must be less than 255 characters. Invalid values: ${valuesTooLong.mkString(", ")}"
            )
          )
        else Right(())
      }
      _ <- {
        val invalidValues = req.tags.values.filterNot(x =>
          x.matches("^([\\p{L}\\p{Z}\\p{N}_.:/=+\\-]*)$")
        )
        if (invalidValues.nonEmpty)
          Left(
            InvalidArgumentException(
              s"Values contain invalid characters. Invalid values: ${invalidValues.mkString(", ")}"
            )
          )
        else Right(())
      }
      _ <- {
        val numberOfTags = req.tags.size
        if (numberOfTags > 10)
          Left(
            InvalidArgumentException(
              s"Can only add 10 tags with a single request. Request contains $numberOfTags tags"
            )
          )
        else Right(())
      }
      _ <- {
        val totalTagsAfterAppend = (stream.tags ++ req.tags).size
        if (totalTagsAfterAppend > 50)
          Left(
            InvalidArgumentException(
              s"AWS resources can only have 50 tags. Request would result in $totalTagsAfterAppend tags"
            )
          )
        else Right(())
      }
    } yield streams.copy(streams =
      streams.streams.filterNot(_.name == req.streamName) :+
        stream.copy(tags = stream.tags ++ req.tags)
    )
}
