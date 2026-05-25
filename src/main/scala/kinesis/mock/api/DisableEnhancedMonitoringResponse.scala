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
package api

import cats.Eq
import io.circe

import kinesis.mock.models.*

final case class DisableEnhancedMonitoringResponse(
    currentShardLevelMetrics: Vector[ShardLevelMetric],
    desiredShardLevelMetrics: Vector[ShardLevelMetric],
    streamName: StreamName,
    streamArn: StreamArn
)

object DisableEnhancedMonitoringResponse:
  given disableEnhancedMonitoringResponseCirceEncoder
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

  given disableEnhancedMonitoringResponseCirceDecoder
      : circe.Decoder[DisableEnhancedMonitoringResponse] = x =>
    for
      currentShardLevelMetrics <- x
        .downField("CurrentShardLevelMetrics")
        .as[Vector[ShardLevelMetric]]
      desiredShardLevelMetrics <- x
        .downField("DesiredShardLevelMetrics")
        .as[Vector[ShardLevelMetric]]
      streamName <- x.downField("StreamName").as[StreamName]
      streamArn <- x.downField("StreamARN").as[StreamArn]
    yield DisableEnhancedMonitoringResponse(
      currentShardLevelMetrics,
      desiredShardLevelMetrics,
      streamName,
      streamArn
    )
  given disableEnhancedMonitoringResponseEncoder
      : Encoder[DisableEnhancedMonitoringResponse] = Encoder.derive
  given disableEnhancedMonitoringResponseDecoder
      : Decoder[DisableEnhancedMonitoringResponse] = Decoder.derive
  given Eq[DisableEnhancedMonitoringResponse] = Eq.fromUniversalEquals
