package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_EnableEnhancedMonitoring.html
final case class EnableEnhancedMonitoringRequest(
    shardLevelMetrics: Vector[ShardLevelMetric],
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def enableEnhancedMonitoring(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[EnableEnhancedMonitoringResponse]] =
    streamsRef.modify { streams =>
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          CommonValidations
            .validateStreamName(name)
            .flatMap(_ => CommonValidations.findStream(arn, streams))
            .map { stream =>
              val current =
                stream.enhancedMonitoring.flatMap(_.shardLevelMetrics)
              val desired =
                if (shardLevelMetrics.contains(ShardLevelMetric.ALL))
                  ShardLevelMetric.values
                    .filterNot(_ == ShardLevelMetric.ALL)
                    .toVector
                else (current ++ shardLevelMetrics).distinct
              (
                streams.updateStream(
                  stream
                    .copy(enhancedMonitoring =
                      Vector(ShardLevelMetrics(desired))
                    )
                ),
                EnableEnhancedMonitoringResponse(
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

object EnableEnhancedMonitoringRequest {
  implicit val enableEnhancedMonitoringRequestCirceEncoder
      : circe.Encoder[EnableEnhancedMonitoringRequest] =
    circe.Encoder.forProduct3("ShardLevelMetrics", "StreamName", "StreamARN")(
      x => (x.shardLevelMetrics, x.streamName, x.streamArn)
    )
  implicit val enableEnhancedMonitoringRequestCirceDecoder
      : circe.Decoder[EnableEnhancedMonitoringRequest] = { x =>
    for {
      shardLevelMetrics <- x
        .downField("ShardLevelMetrics")
        .as[Vector[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    } yield EnableEnhancedMonitoringRequest(
      shardLevelMetrics,
      streamName,
      streamArn
    )
  }
  implicit val enableEnhancedMonitoringRequestEncoder
      : Encoder[EnableEnhancedMonitoringRequest] = Encoder.derive
  implicit val enableEnhancedMonitoringRequestDecoder
      : Decoder[EnableEnhancedMonitoringRequest] = Decoder.derive
  implicit val enableEnhancedMonitoringRequestEq
      : Eq[EnableEnhancedMonitoringRequest] = Eq.fromUniversalEquals
}
