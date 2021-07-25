package kinesis.mock
package api

import scala.collection.SortedMap

import cats.Eq
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

final case class DeleteStreamRequest(
    streamName: StreamName,
    enforceConsumerDeletion: Option[Boolean]
) {
  def deleteStream(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] =
    streamsRef.modify { streams =>
      CommonValidations
        .validateStreamName(streamName)
        .flatMap(_ =>
          CommonValidations
            .findStream(streamName, streams)
            .flatMap(stream =>
              (
                CommonValidations.isStreamActive(streamName, streams),
                if (
                  !enforceConsumerDeletion
                    .getOrElse(false) && stream.consumers.nonEmpty
                )
                  ResourceInUseException(
                    s"Consumers exist in stream $streamName and enforceConsumerDeletion is either not set or is false"
                  ).asLeft
                else Right(())
              ).mapN((_, _) => stream)
            )
        )
        .map { stream =>
          val deletingStream = Map(
            streamName -> stream.copy(
              shards = SortedMap.empty,
              streamStatus = StreamStatus.DELETING,
              tags = Tags.empty,
              enhancedMonitoring = Vector.empty,
              consumers = SortedMap.empty
            )
          )
          (
            streams.copy(
              streams = streams.streams ++ deletingStream
            ),
            ()
          )
        }
        .sequenceWithDefault(streams)
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
