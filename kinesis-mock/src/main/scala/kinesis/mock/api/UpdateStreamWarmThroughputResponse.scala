/*
 * Copyright 2021-2026 io.github.etspaceman
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

final case class UpdateStreamWarmThroughputResponse(
    streamName: Option[StreamName],
    streamArn: Option[StreamArn],
    warmThroughput: WarmThroughput
)

object UpdateStreamWarmThroughputResponse:
  given updateStreamWarmThroughputResponseCirceEncoder
      : circe.Encoder[UpdateStreamWarmThroughputResponse] =
    circe.Encoder.forProduct3(
      "StreamName",
      "StreamARN",
      "WarmThroughput"
    )(x => (x.streamName, x.streamArn, x.warmThroughput))
  given updateStreamWarmThroughputResponseCirceDecoder
      : circe.Decoder[UpdateStreamWarmThroughputResponse] = x =>
    for
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      warmThroughput <- x.downField("WarmThroughput").as[WarmThroughput]
    yield UpdateStreamWarmThroughputResponse(
      streamName,
      streamArn,
      warmThroughput
    )
  given updateStreamWarmThroughputResponseEncoder
      : Encoder[UpdateStreamWarmThroughputResponse] =
    Encoder.derive
  given updateStreamWarmThroughputResponseDecoder
      : Decoder[UpdateStreamWarmThroughputResponse] =
    Decoder.derive
  given Eq[UpdateStreamWarmThroughputResponse] =
    Eq.fromUniversalEquals
