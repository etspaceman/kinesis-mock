package kinesis.mock
package api

import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

final case class RemoveTagsFromStreamRequest(
    streamName: String,
    tagKeys: List[String]
)

object RemoveTagsFromStreamRequest {
  implicit val removeTagsFromStreamRequestEncoder
      : Encoder[RemoveTagsFromStreamRequest] =
    Encoder.forProduct2("StreamName", "TagKeys")(x => (x.streamName, x.tagKeys))
  implicit val removeagsFromStreamRequestDecoder
      : Decoder[RemoveTagsFromStreamRequest] = { x =>
    for {
      streamName <- x.downField("StreamName").as[String]
      tagKeys <- x.downField("TagKeys").as[List[String]]
    } yield RemoveTagsFromStreamRequest(streamName, tagKeys)
  }

  // https://docs.aws.amazon.com/kinesis/latest/APIReference/API_RemoveTagsFromStream.html
  def removeTagsFromStream(
      req: RemoveTagsFromStreamRequest,
      streams: Streams
  ): Either[KinesisMockException, Streams] =
    for {
      _ <- CommonValidations.validateStreamName(req.streamName)
      stream <- Either.fromOption(
        streams.streams.find(_.name == req.streamName),
        ResourceNotFoundException(s"Stream name ${req.streamName} not found")
      )
      _ <- CommonValidations.validateTagKeys(req.tagKeys)
      _ <- {
        val numberOfTags = req.tagKeys.length
        if (numberOfTags > 10)
          Left(
            InvalidArgumentException(
              s"Can only remove 50 tags with a single request. Request contains $numberOfTags tags"
            )
          )
        else Right(())
      }
    } yield streams.copy(streams =
      streams.streams.filterNot(_.name == req.streamName) :+
        stream.copy(tags = stream.tags -- req.tagKeys)
    )
}
