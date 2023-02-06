package kinesis.mock
package api

import scala.collection.SortedMap

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

final case class DeleteStreamRequest(
    streamName: Option[StreamName],
    streamArn: Option[StreamArn],
    enforceConsumerDeletion: Option[Boolean]
) {
  def deleteStream(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[Unit]] =
    streamsRef.modify { streams =>
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          CommonValidations
            .validateStreamName(name)
            .flatMap(_ =>
              CommonValidations
                .findStream(arn, streams)
                .flatMap(stream =>
                  (
                    CommonValidations.isStreamActive(arn, streams),
                    if (
                      !enforceConsumerDeletion
                        .getOrElse(false) && stream.consumers.nonEmpty
                    )
                      ResourceInUseException(
                        s"Consumers exist in stream $name and enforceConsumerDeletion is either not set or is false"
                      ).asLeft
                    else Right(())
                  ).mapN((_, _) => (stream, arn))
                )
            )
            .map { case (stream, arn) =>
              val deletingStream = Map(
                arn -> stream.copy(
                  shards = SortedMap.empty,
                  streamStatus = StreamStatus.DELETING,
                  tags = Tags.empty,
                  enhancedMonitoring = None,
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
        }
        .sequenceWithDefault(streams)
    }
}

object DeleteStreamRequest {
  implicit val deleteStreamRequestCirceEncoder
      : circe.Encoder[DeleteStreamRequest] =
    circe.Encoder.forProduct3(
      "StreamName",
      "StreamARN",
      "EnforceConsumerDeletion"
    )(x => (x.streamName, x.streamArn, x.enforceConsumerDeletion))
  implicit val deleteStreamRequestCirceDecoder
      : circe.Decoder[DeleteStreamRequest] = { x =>
    for {
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      enforceConsumerDeletion <- x
        .downField("EnforceConsumerDeletion")
        .as[Option[Boolean]]
    } yield DeleteStreamRequest(streamName, streamArn, enforceConsumerDeletion)
  }
  implicit val deleteStreamRequestEncoder: Encoder[DeleteStreamRequest] =
    Encoder.derive
  implicit val deleteStreamRequestDecoder: Decoder[DeleteStreamRequest] =
    Decoder.derive
  implicit val deleteStreamRequestEq: Eq[DeleteStreamRequest] =
    Eq.fromUniversalEquals
}
