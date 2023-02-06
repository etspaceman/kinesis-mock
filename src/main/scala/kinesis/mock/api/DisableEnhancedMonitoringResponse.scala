package kinesis.mock
package api

import cats.Eq
import io.circe

import kinesis.mock.models._

final case class DisableEnhancedMonitoringResponse(
    currentShardLevelMetrics: Option[Vector[ShardLevelMetric]],
    desiredShardLevelMetrics: Option[Vector[ShardLevelMetric]],
    streamName: StreamName,
    streamArn: StreamArn
)

object DisableEnhancedMonitoringResponse {
  implicit val disableEnhancedMonitoringResponseCirceEncoder
      : circe.Encoder[DisableEnhancedMonitoringResponse] =
    circe.Encoder.forProduct4(
      "CurrentShardLevelMetrics",
      "DesiredShardLevelMetrics",
      "StreamName",
      "StreamARN"
    )(x =>
      (
        x.currentShardLevelMetrics,
        x.desiredShardLevelMetrics,
        x.streamName,
        x.streamArn
      )
    )

  implicit val disableEnhancedMonitoringResponseCirceDecoder
      : circe.Decoder[DisableEnhancedMonitoringResponse] = { x =>
    for {
      currentShardLevelMetrics <- x
        .downField("CurrentShardLevelMetrics")
        .as[Option[Vector[ShardLevelMetric]]]
      desiredShardLevelMetrics <- x
        .downField("DesiredShardLevelMetrics")
        .as[Option[Vector[ShardLevelMetric]]]
      streamName <- x.downField("StreamName").as[StreamName]
      streamArn <- x.downField("StreamARN").as[StreamArn]
    } yield DisableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamName,
      streamArn
    )
  }
  implicit val disableEnhancedMonitoringResponseEncoder
      : Encoder[DisableEnhancedMonitoringResponse] = Encoder.derive
  implicit val disableEnhancedMonitoringResponseDecoder
      : Decoder[DisableEnhancedMonitoringResponse] = Decoder.derive
  implicit val disableEnhancedMonitoringResponseEq
      : Eq[DisableEnhancedMonitoringResponse] = Eq.fromUniversalEquals
}
