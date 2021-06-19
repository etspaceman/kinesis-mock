package kinesis.mock
package models

import cats.Eq
import io.circe

final case class ShardLevelMetrics(shardLevelMetrics: List[ShardLevelMetric])

object ShardLevelMetrics {
  implicit val shardLevelMetricsCirceEncoder: circe.Encoder[ShardLevelMetrics] =
    circe.Encoder.forProduct1("ShardLevelMetrics")(_.shardLevelMetrics)
  implicit val shardLevelMetricsCirceDecoder
      : circe.Decoder[ShardLevelMetrics] = {
    _.downField("ShardLevelMetrics")
      .as[List[ShardLevelMetric]]
      .map(ShardLevelMetrics.apply)
  }
  implicit val shardLevelMetricsEncoder: Encoder[ShardLevelMetrics] =
    Encoder.derive
  implicit val shardLevelMetricsDecoder: Decoder[ShardLevelMetrics] =
    Decoder.derive
  implicit val shardLevelMetricsEq: Eq[ShardLevelMetrics] =
    Eq.fromUniversalEquals
}
