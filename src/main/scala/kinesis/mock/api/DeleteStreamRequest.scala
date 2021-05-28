package kinesis.mock
package api

import scala.collection.SortedMap

import cats.data.Validated._
import cats.effect.IO
import cats.effect.concurrent.{Ref, Semaphore}
import cats.kernel.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class DeleteStreamRequest(
    streamName: StreamName,
    enforceConsumerDeletion: Option[Boolean]
) {
  def deleteStream(
      streamsRef: Ref[IO, Streams],
      shardSemaphoresRef: Ref[IO, Map[ShardSemaphoresKey, Semaphore[IO]]]
  ): IO[ValidatedResponse[Unit]] =
    streamsRef.get.flatMap { streams =>
      CommonValidations
        .validateStreamName(streamName)
        .andThen(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .andThen(stream =>
              (
                CommonValidations.isStreamActive(streamName, streams),
                if (
                  enforceConsumerDeletion
                    .exists(identity) && stream.consumers.nonEmpty
                )
                  ResourceInUseException(
                    s"Consumers exist in stream $streamName and enforceConsumerDeletion is true"
                  ).invalidNel
                else Valid(())
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

          val shardSemaphoreKeys = stream.shards.keys.toList
            .map(shard => ShardSemaphoresKey(stream.streamName, shard))

          for {
            _ <- streamsRef
              .update(streams =>
                streams.copy(
                  streams = streams.streams ++ deletingStream
                )
              )
            res <- shardSemaphoresRef
              .update(shardSemaphores => shardSemaphores -- shardSemaphoreKeys)
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
