package kinesis.mock
package api

import cats.kernel.Eq
import io.circe

import kinesis.mock.models._

final case class EnableEnhancedMonitoringResponse(
    currentShardLevelMetrics: List[ShardLevelMetric],
    desiredShardLevelMetrics: List[ShardLevelMetric],
    streamName: StreamName
)

object EnableEnhancedMonitoringResponse {
  implicit val enableEnhancedMonitoringResponseCirceEncoder
      : circe.Encoder[EnableEnhancedMonitoringResponse] =
    circe.Encoder.forProduct3(
      "CurrentShardLevelMetrics",
      "DesiredShardLevelMetrics",
      "StreamName"
    )(x =>
      (x.currentShardLevelMetrics, x.desiredShardLevelMetrics, x.streamName)
    )

  implicit val enableEnhancedMonitoringResponseCirceDecoder
      : circe.Decoder[EnableEnhancedMonitoringResponse] = { x =>
    for {
      currentShardLevelMetrics <- x
        .downField("CurrentShardLevelMetrics")
        .as[List[ShardLevelMetric]]
      desiredShardLevelMetrics <- x
        .downField("DesiredShardLevelMetrics")
        .as[List[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield EnableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamName
    )
  }
  implicit val enableEnhancedMonitoringResponseEncoder
      : Encoder[EnableEnhancedMonitoringResponse] = Encoder.derive
  implicit val enableEnhancedMonitoringResponseDecoder
      : Decoder[EnableEnhancedMonitoringResponse] = Decoder.derive
  implicit val enableEnhancedMonitoringResponseEq
      : Eq[EnableEnhancedMonitoringResponse] = Eq.fromUniversalEquals
}
