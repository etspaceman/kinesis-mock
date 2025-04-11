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

final case class SequenceNumberRange(
    endingSequenceNumber: Option[SequenceNumber],
    startingSequenceNumber: SequenceNumber
)

object SequenceNumberRange:
  given sequenceNumberRangeCirceEncoder: circe.Encoder[SequenceNumberRange] =
    circe.Encoder.forProduct2("EndingSequenceNumber", "StartingSequenceNumber")(
      x => (x.endingSequenceNumber, x.startingSequenceNumber)
    )

  given sequenceNumberRangeCirceDecoder: circe.Decoder[SequenceNumberRange] =
    x =>
      for
        endingSequenceNumber <- x
          .downField("EndingSequenceNumber")
          .as[Option[SequenceNumber]]
        startingSequenceNumber <- x
          .downField("StartingSequenceNumber")
          .as[SequenceNumber]
      yield SequenceNumberRange(endingSequenceNumber, startingSequenceNumber)

  given sequenceNumberRangeEncoder: Encoder[SequenceNumberRange] =
    Encoder.derive

  given sequenceNumberRangeDecoder: Decoder[SequenceNumberRange] =
    Decoder.derive

  given sequenceNumberRangeEq: Eq[SequenceNumberRange] =
    Eq.fromUniversalEquals
