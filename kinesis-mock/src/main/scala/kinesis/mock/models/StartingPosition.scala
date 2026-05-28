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

import java.time.Instant

import cats.Eq
import io.circe

import kinesis.mock.instances.circe.*

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_StartingPosition.html
final case class StartingPosition(
    `type`: ShardIteratorType,
    sequenceNumber: Option[SequenceNumber],
    timestamp: Option[Instant]
)

object StartingPosition:
  def startingPositionCirceEncoder(using
      EI: circe.Encoder[Instant]
  ): circe.Encoder[StartingPosition] =
    circe.Encoder.forProduct3("Type", "SequenceNumber", "Timestamp")(sp =>
      (sp.`type`, sp.sequenceNumber, sp.timestamp)
    )

  def startingPositionCirceDecoder(using
      DI: circe.Decoder[Instant]
  ): circe.Decoder[StartingPosition] =
    c =>
      for
        t <- c.downField("Type").as[ShardIteratorType]
        sn <- c.downField("SequenceNumber").as[Option[SequenceNumber]]
        ts <- c.downField("Timestamp").as[Option[Instant]]
      yield StartingPosition(t, sn, ts)

  given startingPositionEncoder: Encoder[StartingPosition] =
    Encoder.instance(
      startingPositionCirceEncoder(using instantBigDecimalCirceEncoder),
      startingPositionCirceEncoder(using instantLongCirceEncoder)
    )

  given startingPositionDecoder: Decoder[StartingPosition] =
    Decoder.instance(
      startingPositionCirceDecoder(using instantBigDecimalCirceDecoder),
      startingPositionCirceDecoder(using instantLongCirceDecoder)
    )

  given Eq[StartingPosition] = (x, y) =>
    x.`type` == y.`type` &&
      x.sequenceNumber == y.sequenceNumber &&
      x.timestamp.map(_.getEpochSecond()) ==
      y.timestamp.map(_.getEpochSecond())
