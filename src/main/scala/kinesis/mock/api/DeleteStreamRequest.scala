package kinesis.mock
package api

import io.circe._

import kinesis.mock.models._

final case class DeleteStreamRequest(
    streamName: String,
    enforceConsumerDeletion: Option[Boolean]
) {
  def deleteStream(
      streams: Streams
  ): Either[KinesisMockException, Streams] =
    for {
      _ <- CommonValidations.validateStreamName(streamName)
      stream <- CommonValidations.findStream(streamName, streams)
      _ <- CommonValidations.isStreamActive(streamName, streams)
      _ <-
        if (
          enforceConsumerDeletion.exists(identity) && stream.consumers.nonEmpty
        )
          Left(
            ResourceInUseException(
              s"Consumers exist in stream $streamName and enforceConsumerDeletion is true"
            )
          )
        else Right(())
    } yield streams.deleteStream(streamName)
}

object DeleteStreamRequest {
  implicit val deleteStreamRequestCirceEncoder: Encoder[DeleteStreamRequest] =
    Encoder.forProduct2("StreamName", "EnforceConsumerDeletion")(x =>
      (x.streamName, x.enforceConsumerDeletion)
    )
  implicit val deleteStreamRequestCirceDecoder: Decoder[DeleteStreamRequest] = {
    x =>
      for {
        streamName <- x.downField("StreamName").as[String]
        enforceConsumerDeletion <- x
          .downField("EnforceConsumerDeletion")
          .as[Option[Boolean]]
      } yield DeleteStreamRequest(streamName, enforceConsumerDeletion)

  }
}
