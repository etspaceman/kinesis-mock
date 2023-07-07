/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock
package models

import cats.Eq
import io.circe

final case class ShardLevelMetrics(shardLevelMetrics: Vector[ShardLevelMetric])

object ShardLevelMetrics {
  implicit val shardLevelMetricsCirceEncoder: circe.Encoder[ShardLevelMetrics] =
    circe.Encoder.forProduct1("ShardLevelMetrics")(_.shardLevelMetrics)
  implicit val shardLevelMetricsCirceDecoder: circe.Decoder[ShardLevelMetrics] =
    _.downField("ShardLevelMetrics")
      .as[Vector[ShardLevelMetric]]
      .map(ShardLevelMetrics.apply)
  implicit val shardLevelMetricsEncoder: Encoder[ShardLevelMetrics] =
    Encoder.derive
  implicit val shardLevelMetricsDecoder: Decoder[ShardLevelMetrics] =
    Decoder.derive
  implicit val shardLevelMetricsEq: Eq[ShardLevelMetrics] =
    Eq.fromUniversalEquals
}
