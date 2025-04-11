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

final case class UpdateShardCountResponse(
    currentShardCount: Int,
    streamName: StreamName,
    targetShardCount: Int
)

object UpdateShardCountResponse:
  given updateShardCountResponseCirceEncoder
      : circe.Encoder[UpdateShardCountResponse] =
    circe.Encoder.forProduct3(
      "CurrentShardCount",
      "StreamName",
      "TargetShardCount"
    )(x => (x.currentShardCount, x.streamName, x.targetShardCount))
  given updateShardCountResponseCirceDecoder
      : circe.Decoder[UpdateShardCountResponse] = x =>
    for
      currentShardCount <- x.downField("CurrentShardCount").as[Int]
      streamName <- x.downField("StreamName").as[StreamName]
      targetShardCount <- x.downField("TargetShardCount").as[Int]
    yield UpdateShardCountResponse(
      currentShardCount,
      streamName,
      targetShardCount
    )
  given updateShardCountResponseEncoder: Encoder[UpdateShardCountResponse] =
    Encoder.derive
  given updateShardCountResponseDecoder: Decoder[UpdateShardCountResponse] =
    Decoder.derive
  given updateShardCountResponseEq: Eq[UpdateShardCountResponse] =
    Eq.fromUniversalEquals
