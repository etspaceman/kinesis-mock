package kinesis.mock.api

import cats.kernel.Eq
import io.circe._

import kinesis.mock.models.ShardLevelMetric

final case class DisableEnhancedMonitoringResponse(
    currentShardLevelMetrics: List[ShardLevelMetric],
    desiredShardLevelMetrics: List[ShardLevelMetric],
    streamName: String
)

object DisableEnhancedMonitoringResponse {
  implicit val disableEnhancedMonitoringResponseCirceEncoder
      : Encoder[DisableEnhancedMonitoringResponse] =
    Encoder.forProduct3(
      "CurrentShardLevelMetrics",
      "DesiredShardLevelMetrics",
      "StreamName"
    )(x =>
      (x.currentShardLevelMetrics, x.desiredShardLevelMetrics, x.streamName)
    )

  implicit val disableEnhancedMonitoringResponseCirceDecoder
      : Decoder[DisableEnhancedMonitoringResponse] = { x =>
    for {
      currentShardLevelMetrics <- x
        .downField("CurrentShardLevelMetrics")
        .as[List[ShardLevelMetric]]
      desiredShardLevelMetrics <- x
        .downField("DesiredShardLevelMetrics")
        .as[List[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[String]
    } yield DisableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamName
    )
  }
  implicit val disableEnhancedMonitoringResponseEq
      : Eq[DisableEnhancedMonitoringResponse] = Eq.fromUniversalEquals
}
