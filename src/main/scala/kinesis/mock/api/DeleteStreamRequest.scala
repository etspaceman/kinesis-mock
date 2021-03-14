package kinesis.mock
package api

import cats.data.Validated._
import cats.data._
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

final case class DeleteStreamRequest(
    streamName: String,
    enforceConsumerDeletion: Option[Boolean]
) {
  def deleteStream(
      streams: Streams
  ): ValidatedNel[KinesisMockException, (Streams, List[ShardSemaphoresKey])] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen(stream =>
        (
          CommonValidations.validateStreamName(streamName),
          CommonValidations.isStreamActive(streamName, streams),
          if (
            enforceConsumerDeletion
              .exists(identity) && stream.consumers.nonEmpty
          )
            ResourceInUseException(
              s"Consumers exist in stream $streamName and enforceConsumerDeletion is true"
            ).invalidNel
          else Valid(())
        ).mapN((_, _, _) => streams.deleteStream(streamName))
      )
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
