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
package models

import cats.Eq
import io.circe

final case class WarmThroughput(
    currentMiBps: Int,
    targetMiBps: Int
)

object WarmThroughput:
  given warmThroughputCirceEncoder: circe.Encoder[WarmThroughput] =
    circe.Encoder.forProduct2(
      "CurrentMiBps",
      "TargetMiBps"
    )(x => (x.currentMiBps, x.targetMiBps))

  given warmThroughputCirceDecoder: circe.Decoder[WarmThroughput] = x =>
    for
      currentMiBps <- x.downField("CurrentMiBps").as[Int]
      targetMiBps <- x.downField("TargetMiBps").as[Int]
    yield WarmThroughput(currentMiBps, targetMiBps)

  given warmThroughputEncoder: Encoder[WarmThroughput] = Encoder.derive
  given warmThroughputDecoder: Decoder[WarmThroughput] = Decoder.derive

  given Eq[WarmThroughput] = Eq.fromUniversalEquals
