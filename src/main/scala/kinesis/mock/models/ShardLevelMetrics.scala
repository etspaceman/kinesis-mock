package kinesis.mock.models

import cats.kernel.Eq
import io.circe._

final case class ShardLevelMetrics(shardLevelMetrics: List[ShardLevelMetric])

object ShardLevelMetrics {
  implicit val shardLevelMetricsCirceEncoder: Encoder[ShardLevelMetrics] =
    Encoder.forProduct1("ShardLevelMetrics")(_.shardLevelMetrics)
  implicit val shardLevelMetricsCirceDecoder: Decoder[ShardLevelMetrics] = {
    _.downField("ShardLevelMetrics")
      .as[List[ShardLevelMetric]]
      .map(ShardLevelMetrics.apply)
  }
  implicit val shardLevelMetricsEq: Eq[ShardLevelMetrics] =
    Eq.fromUniversalEquals
}
