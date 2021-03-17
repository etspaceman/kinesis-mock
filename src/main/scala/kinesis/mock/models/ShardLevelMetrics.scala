package kinesis.mock.models

import io.circe._
import cats.kernel.Eq

final case class ShardLevelMetrics(shardLevelMetrics: List[ShardLevelMetric])

object ShardLevelMetrics {
  implicit val shardLevelMetricsCirceEncoder: Encoder[ShardLevelMetrics] =
    Encoder.forProduct1("ShardLevelMetrics")(_.shardLevelMetrics)
  implicit val shardLevelMetricsCirceDecoder: Decoder[ShardLevelMetrics] = {
    _.downField("ShardLevelMetrics")
      .as[List[ShardLevelMetric]]
      .map(ShardLevelMetrics.apply)
  }
  implicit val shardLevelMetricsEq: Eq[ShardLevelMetrics] = Eq.fromUniversalEquals
}
