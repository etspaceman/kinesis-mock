package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DisableEnhancedMonitoring.html
final case class DisableEnhancedMonitoringRequest(
    shardLevelMetrics: Vector[ShardLevelMetric],
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def disableEnhancedMonitoring(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[DisableEnhancedMonitoringResponse]] =
    streamsRef.modify { streams =>
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          CommonValidations
            .validateStreamName(name)
            .flatMap(_ => CommonValidations.findStream(arn, streams))
            .map { stream =>
              val current =
                stream.enhancedMonitoring.map(_.flatMap(_.shardLevelMetrics))
              val desired: Option[Vector[ShardLevelMetric]] =
                if (shardLevelMetrics.contains(ShardLevelMetric.ALL))
                  None
                else current.map(_.diff(shardLevelMetrics))

              (
                streams.updateStream(
                  stream
                    .copy(enhancedMonitoring =
                      desired.map(x => Vector(ShardLevelMetrics(x)))
                    )
                ),
                DisableEnhancedMonitoringResponse(
                  current,
                  desired,
                  name,
                  arn
                )
              )
            }
        }
        .sequenceWithDefault(streams)
    }
}

object DisableEnhancedMonitoringRequest {
  implicit val disableEnhancedMonitoringRequestCirceEncoder
      : circe.Encoder[DisableEnhancedMonitoringRequest] =
    circe.Encoder.forProduct3("ShardLevelMetrics", "StreamName", "StreamARN")(
      x => (x.shardLevelMetrics, x.streamName, x.streamArn)
    )
  implicit val disableEnhancedMonitoringRequestCirceDecoder
      : circe.Decoder[DisableEnhancedMonitoringRequest] = { x =>
    for {
      shardLevelMetrics <- x
        .downField("ShardLevelMetrics")
        .as[Vector[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    } yield DisableEnhancedMonitoringRequest(
      shardLevelMetrics,
      streamName,
      streamArn
    )
  }
  implicit val disableEnhancedMonitoringRequestEncoder
      : Encoder[DisableEnhancedMonitoringRequest] = Encoder.derive
  implicit val disableEnhancedMonitoringRequestDecoder
      : Decoder[DisableEnhancedMonitoringRequest] = Decoder.derive
  implicit val disableEnhancedMonitoringRequestEq
      : Eq[DisableEnhancedMonitoringRequest] = Eq.fromUniversalEquals
}
