package kinesis.mock
package api

import scala.collection.SortedMap

import cats.Eq
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class DeleteStreamRequest(
    streamName: StreamName,
    enforceConsumerDeletion: Option[Boolean]
) {
  def deleteStream(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] =
    streamsRef.get.flatMap { streams =>
      CommonValidations
        .validateStreamName(streamName)
        .flatMap(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .flatMap(stream =>
              (
                CommonValidations.isStreamActive(streamName, streams),
                if (
                  enforceConsumerDeletion
                    .exists(identity) && stream.consumers.nonEmpty
                )
                  ResourceInUseException(
                    s"Consumers exist in stream $streamName and enforceConsumerDeletion is true"
                  ).asLeft
                else Right(())
              ).mapN((_, _) => stream)
            )
        )
        .traverse { stream =>
          val deletingStream = SortedMap(
            streamName -> stream.copy(
              shards = SortedMap.empty,
              streamStatus = StreamStatus.DELETING,
              tags = Tags.empty,
              enhancedMonitoring = List.empty,
              consumers = SortedMap.empty
            )
          )

          for {
            res <- streamsRef
              .update(streams =>
                streams.copy(
                  streams = streams.streams ++ deletingStream
                )
              )
          } yield res
        }
    }
}

object DeleteStreamRequest {
  implicit val deleteStreamRequestCirceEncoder
      : circe.Encoder[DeleteStreamRequest] =
    circe.Encoder.forProduct2("StreamName", "EnforceConsumerDeletion")(x =>
      (x.streamName, x.enforceConsumerDeletion)
    )
  implicit val deleteStreamRequestCirceDecoder
      : circe.Decoder[DeleteStreamRequest] = { x =>
    for {
      streamName <- x.downField("StreamName").as[StreamName]
      enforceConsumerDeletion <- x
        .downField("EnforceConsumerDeletion")
        .as[Option[Boolean]]
    } yield DeleteStreamRequest(streamName, enforceConsumerDeletion)
  }
  implicit val deleteStreamRequestEncoder: Encoder[DeleteStreamRequest] =
    Encoder.derive
  implicit val deleteStreamRequestDecoder: Decoder[DeleteStreamRequest] =
    Decoder.derive
  implicit val deleteStreamRequestEq: Eq[DeleteStreamRequest] =
    Eq.fromUniversalEquals
}
