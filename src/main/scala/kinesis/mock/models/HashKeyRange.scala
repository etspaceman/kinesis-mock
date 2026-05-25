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
import io.circe.syntax.*

final case class HashKeyRange(endingHashKey: BigInt, startingHashKey: BigInt):
  def isAdjacent(other: HashKeyRange): Boolean =
    endingHashKey == other.startingHashKey + BigInt(1) ||
      startingHashKey == other.endingHashKey + BigInt(1)

object HashKeyRange:
  given hashKeyRangeCirceEncoder: circe.Encoder[HashKeyRange] = x =>
    circe
      .JsonObject(
        "EndingHashKey" -> x.endingHashKey.toString.asJson,
        "StartingHashKey" -> x.startingHashKey.toString.asJson
      )
      .asJson

  given hashKeyRangeCirceDecoder: circe.Decoder[HashKeyRange] = x =>
    for
      endingHashKey <- x.downField("EndingHashKey").as[String].map(BigInt.apply)
      startingHashKey <- x
        .downField("StartingHashKey")
        .as[String]
        .map(BigInt.apply)
    yield HashKeyRange(endingHashKey, startingHashKey)

  given hashKeyRangeEncoder: Encoder[HashKeyRange] =
    Encoder.derive
  given hashKeyRangeDecoder: Decoder[HashKeyRange] =
    Decoder.derive

  given Eq[HashKeyRange] = Eq.fromUniversalEquals
