package kinesis.mock
package api

import cats.Eq
import io.circe

import kinesis.mock.models._

final case class EnableEnhancedMonitoringResponse(
    currentShardLevelMetrics: Vector[ShardLevelMetric],
    desiredShardLevelMetrics: Vector[ShardLevelMetric],
    streamName: StreamName,
    streamArn: StreamArn
)

object EnableEnhancedMonitoringResponse {
  implicit val enableEnhancedMonitoringResponseCirceEncoder
      : circe.Encoder[EnableEnhancedMonitoringResponse] =
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

  implicit val enableEnhancedMonitoringResponseCirceDecoder
      : circe.Decoder[EnableEnhancedMonitoringResponse] = { x =>
    for {
      currentShardLevelMetrics <- x
        .downField("CurrentShardLevelMetrics")
        .as[Vector[ShardLevelMetric]]
      desiredShardLevelMetrics <- x
        .downField("DesiredShardLevelMetrics")
        .as[Vector[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[StreamName]
      streamArn <- x.downField("StreamARN").as[StreamArn]
    } yield EnableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamName,
      streamArn
    )
  }
  implicit val enableEnhancedMonitoringResponseEncoder
      : Encoder[EnableEnhancedMonitoringResponse] = Encoder.derive
  implicit val enableEnhancedMonitoringResponseDecoder
      : Decoder[EnableEnhancedMonitoringResponse] = Decoder.derive
  implicit val enableEnhancedMonitoringResponseEq
      : Eq[EnableEnhancedMonitoringResponse] = Eq.fromUniversalEquals
}
