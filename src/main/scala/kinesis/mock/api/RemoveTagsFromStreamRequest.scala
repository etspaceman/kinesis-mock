package kinesis.mock
package api

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
  ): Either[KinesisMockException, Streams] =
    for {
      _ <- CommonValidations.validateStreamName(streamName)
      stream <- CommonValidations.findStream(streamName, streams)
      _ <- CommonValidations.validateTagKeys(tagKeys)
      _ <- {
        val numberOfTags = tagKeys.length
        if (numberOfTags > 10)
          Left(
            InvalidArgumentException(
              s"Can only remove 50 tags with a single request. Request contains $numberOfTags tags"
            )
          )
        else Right(())
      }
    } yield streams.copy(streams =
      streams.streams.filterNot(_.name == streamName) :+
        stream.copy(tags = stream.tags -- tagKeys)
    )
}

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

}
