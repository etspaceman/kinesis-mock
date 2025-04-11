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

package kinesis.mock.models

import java.time.Instant

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval
import eu.timepit.refined.scalacheck.numeric.*
import eu.timepit.refined.types.numeric.PosInt
import org.scalacheck.Prop.*

class SequenceNumberTests extends munit.ScalaCheckSuite:
  property("It should create and parse correctly")(forAll {
    (
        shardCreateTimeEpochSeconds: Long Refined
          Interval.Closed[
            0L,
            16025174999L
          ],
        shardIndex: Int Refined Interval.Closed[0, 1000],
        seqIndex: PosInt,
        seqTimeEpochSeconds: Long Refined
          Interval.Closed[
            0L,
            16025174999L
          ]
    ) =>
      val shardCreateTime =
        Instant.ofEpochSecond(shardCreateTimeEpochSeconds.value.toLong)
      val seqTime =
        Instant.ofEpochSecond(seqTimeEpochSeconds.value.toLong)

      val sequenceNumber =
        SequenceNumber.create(
          shardCreateTime,
          shardIndex.value,
          None,
          Some(seqIndex.value),
          Some(seqTime)
        )

      val parsed = sequenceNumber.parse

      (parsed.isRight && parsed.exists {
        case x: SequenceNumberParts =>
          seqIndex.value == x.seqIndex &&
          x.shardCreateTime.getEpochSecond == shardCreateTime.getEpochSecond &&
          seqTime.getEpochSecond == x.seqTime.getEpochSecond &&
          x.shardIndex == shardIndex.value

        case _ => false
      }) :| s"shardCreateTime: $shardCreateTime\n" +
        s"shardIndex: ${shardIndex.value}\n" +
        s"seqIndex: ${seqIndex.value}\n" +
        s"seqTime: $seqTime\n" +
        s"Parsed: $parsed\n" +
        s"SequenceNumber: $sequenceNumber"
  })

  property("It should substitute shardCreateTime for seqTime")(forAll {
    (
        shardCreateTimeEpochSeconds: Long Refined
          Interval.Closed[
            0L,
            16025174999L
          ],
        shardIndex: Int Refined Interval.Closed[0, 1000],
        seqIndex: PosInt
    ) =>
      val shardCreateTime =
        Instant.ofEpochSecond(shardCreateTimeEpochSeconds.value.toLong)

      val sequenceNumber =
        SequenceNumber.create(
          shardCreateTime,
          shardIndex.value,
          None,
          Some(seqIndex.value),
          None
        )

      val parsed = sequenceNumber.parse

      (parsed.isRight && parsed.exists {
        case x: SequenceNumberParts =>
          seqIndex.value == x.seqIndex &&
          x.shardCreateTime.getEpochSecond == shardCreateTime.getEpochSecond &&
          shardCreateTime.getEpochSecond == x.seqTime.getEpochSecond &&
          x.shardIndex == shardIndex.value

        case _ => false
      }) :| s"shardCreateTime: $shardCreateTime\n" +
        s"shardIndex: ${shardIndex.value}\n" +
        s"seqIndex: ${seqIndex.value}\n" +
        s"Parsed: $parsed\n" +
        s"SequenceNumber: $sequenceNumber"
  })

  property("It should substitute 0 for seqIndex")(forAll {
    (
        shardCreateTimeEpochSeconds: Long Refined
          Interval.Closed[
            0L,
            16025174999L
          ],
        shardIndex: Int Refined Interval.Closed[0, 1000],
        seqTimeEpochSeconds: Long Refined
          Interval.Closed[
            0L,
            16025174999L
          ]
    ) =>
      val shardCreateTime =
        Instant.ofEpochSecond(shardCreateTimeEpochSeconds.value.toLong)
      val seqTime =
        Instant.ofEpochSecond(seqTimeEpochSeconds.value.toLong)

      val sequenceNumber =
        SequenceNumber.create(
          shardCreateTime,
          shardIndex.value,
          None,
          None,
          Some(seqTime)
        )

      val parsed = sequenceNumber.parse

      (parsed.isRight && parsed.exists {
        case x: SequenceNumberParts =>
          x.seqIndex == 0 &&
          x.shardCreateTime.getEpochSecond == shardCreateTime.getEpochSecond &&
          seqTime.getEpochSecond == x.seqTime.getEpochSecond &&
          x.shardIndex == shardIndex.value

        case _ => false
      }) :| s"shardCreateTime: $shardCreateTime=\n" +
        s"shardIndex: ${shardIndex.value}\n" +
        s"seqTime: $seqTime\n" +
        s"Parsed: $parsed\n" +
        s"SequenceNumber: $sequenceNumber"
  })
