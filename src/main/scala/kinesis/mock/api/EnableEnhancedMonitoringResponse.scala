package kinesis.mock.api

import io.circe._

import kinesis.mock.models.ShardLevelMetric
import cats.kernel.Eq

final case class EnableEnhancedMonitoringResponse(
    currentShardLevelMetrics: List[ShardLevelMetric],
    desiredShardLevelMetrics: List[ShardLevelMetric],
    streamName: String
)

object EnableEnhancedMonitoringResponse {
  implicit val enableEnhancedMonitoringResponseCirceEncoder
      : Encoder[EnableEnhancedMonitoringResponse] =
    Encoder.forProduct3(
      "CurrentShardLevelMetrics",
      "DesiredShardLevelMetrics",
      "StreamName"
    )(x =>
      (x.currentShardLevelMetrics, x.desiredShardLevelMetrics, x.streamName)
    )

  implicit val enableEnhancedMonitoringResponseCirceDecoder
      : Decoder[EnableEnhancedMonitoringResponse] = { x =>
    for {
      currentShardLevelMetrics <- x
        .downField("CurrentShardLevelMetrics")
        .as[List[ShardLevelMetric]]
      desiredShardLevelMetrics <- x
        .downField("DesiredShardLevelMetrics")
        .as[List[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[String]
    } yield EnableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamName
    )
  }
  implicit val enableEnhancedMonitoringResponseEq
      : Eq[EnableEnhancedMonitoringResponse] = Eq.fromUniversalEquals
}
