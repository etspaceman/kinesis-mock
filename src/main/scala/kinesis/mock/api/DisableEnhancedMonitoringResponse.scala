package kinesis.mock
package api

import cats.Eq
import io.circe

import kinesis.mock.models._

final case class DisableEnhancedMonitoringResponse(
    currentShardLevelMetrics: Vector[ShardLevelMetric],
    desiredShardLevelMetrics: Vector[ShardLevelMetric],
    streamName: StreamName
)

object DisableEnhancedMonitoringResponse {
  implicit val disableEnhancedMonitoringResponseCirceEncoder
      : circe.Encoder[DisableEnhancedMonitoringResponse] =
    circe.Encoder.forProduct3(
      "CurrentShardLevelMetrics",
      "DesiredShardLevelMetrics",
      "StreamName"
    )(x =>
      (x.currentShardLevelMetrics, x.desiredShardLevelMetrics, x.streamName)
    )

  implicit val disableEnhancedMonitoringResponseCirceDecoder
      : circe.Decoder[DisableEnhancedMonitoringResponse] = { x =>
    for {
      currentShardLevelMetrics <- x
        .downField("CurrentShardLevelMetrics")
        .as[Vector[ShardLevelMetric]]
      desiredShardLevelMetrics <- x
        .downField("DesiredShardLevelMetrics")
        .as[Vector[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield DisableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamName
    )
  }
  implicit val disableEnhancedMonitoringResponseEncoder
      : Encoder[DisableEnhancedMonitoringResponse] = Encoder.derive
  implicit val disableEnhancedMonitoringResponseDecoder
      : Decoder[DisableEnhancedMonitoringResponse] = Decoder.derive
  implicit val disableEnhancedMonitoringResponseEq
      : Eq[DisableEnhancedMonitoringResponse] = Eq.fromUniversalEquals
}
