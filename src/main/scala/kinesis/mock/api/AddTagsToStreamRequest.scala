package kinesis.mock
package api

import io.circe._

import kinesis.mock.models._

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_AddTagsToStream.html
// https://docs.aws.amazon.com/streams/latest/dev/tagging.html
// https://docs.aws.amazon.com/directoryservice/latest/devguide/API_Tag.html
final case class AddTagsToStreamRequest(
    streamName: String,
    tags: Map[String, String]
) {
  def addTagsToStream(streams: Streams): Either[KinesisMockException, Streams] =
    for {
      _ <- CommonValidations.validateStreamName(streamName)
      stream <- CommonValidations.findStream(streamName, streams)
      _ <- CommonValidations.validateTagKeys(tags.keys)
      _ <- {
        val valuesTooLong = tags.values.filter(x => x.length() > 255)
        if (valuesTooLong.nonEmpty)
          Left(
            InvalidArgumentException(
              s"Values must be less than 255 characters. Invalid values: ${valuesTooLong.mkString(", ")}"
            )
          )
        else Right(())
      }
      _ <- {
        val invalidValues = tags.values.filterNot(x =>
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
        val numberOfTags = tags.size
        if (numberOfTags > 10)
          Left(
            InvalidArgumentException(
              s"Can only add 10 tags with a single request. Request contains $numberOfTags tags"
            )
          )
        else Right(())
      }
      _ <- {
        val totalTagsAfterAppend = (stream.tags ++ tags).size
        if (totalTagsAfterAppend > 50)
          Left(
            InvalidArgumentException(
              s"AWS resources can only have 50 tags. Request would result in $totalTagsAfterAppend tags"
            )
          )
        else Right(())
      }
    } yield streams.copy(streams =
      streams.streams.filterNot(_.name == streamName) :+
        stream.copy(tags = stream.tags ++ tags)
    )
}

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
}
