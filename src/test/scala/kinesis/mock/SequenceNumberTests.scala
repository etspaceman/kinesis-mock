package kinesis.mock

import java.time.Instant

import eu.timepit.refined.W
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Interval
import eu.timepit.refined.scalacheck.numeric._
import eu.timepit.refined.types.numeric.PosInt
import org.scalacheck.Prop._

import kinesis.mock.models._

class SequenceNumberTests extends munit.ScalaCheckSuite {
  property("It should create and parse correctly")(forAll {
    (
        shardCreateTimeEpochSeconds: Long Refined Interval.Closed[
          W.`0L`.T,
          W.`16025174999L`.T
        ],
        shardIndex: Int Refined Interval.Closed[W.`0`.T, W.`1000`.T],
        seqIndex: PosInt,
        seqTimeEpochSeconds: Long Refined Interval.Closed[
          W.`0L`.T,
          W.`16025174999L`.T
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

      (parsed.isValid && parsed.exists {
        case x: SequenceNumberParts =>
          seqIndex.value == x.seqIndex &&
            x.shardCreateTime.getEpochSecond == shardCreateTime.getEpochSecond &&
            seqTime.getEpochSecond() == x.seqTime.getEpochSecond() &&
            x.shardIndex == shardIndex.value

        case _ => false
      }) :| s"shardCreateTime: ${shardCreateTime}\n" +
        s"shardIndex: ${shardIndex.value}\n" +
        s"seqIndex: ${seqIndex.value}\n" +
        s"seqTime: ${seqTime}\n" +
        s"Parsed: $parsed\n" +
        s"SequenceNumber: $sequenceNumber"
  })

  property("It should substitute shardCreateTime for seqTime")(forAll {
    (
        shardCreateTimeEpochSeconds: Long Refined Interval.Closed[
          W.`0L`.T,
          W.`16025174999L`.T
        ],
        shardIndex: Int Refined Interval.Closed[W.`0`.T, W.`1000`.T],
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

      (parsed.isValid && parsed.exists {
        case x: SequenceNumberParts =>
          seqIndex.value == x.seqIndex &&
            x.shardCreateTime.getEpochSecond == shardCreateTime.getEpochSecond &&
            shardCreateTime.getEpochSecond() == x.seqTime.getEpochSecond() &&
            x.shardIndex == shardIndex.value

        case _ => false
      }) :| s"shardCreateTime: ${shardCreateTime}\n" +
        s"shardIndex: ${shardIndex.value}\n" +
        s"seqIndex: ${seqIndex.value}\n" +
        s"Parsed: $parsed\n" +
        s"SequenceNumber: $sequenceNumber"
  })

  property("It should substitute 0 for seqIndex")(forAll {
    (
        shardCreateTimeEpochSeconds: Long Refined Interval.Closed[
          W.`0L`.T,
          W.`16025174999L`.T
        ],
        shardIndex: Int Refined Interval.Closed[W.`0`.T, W.`1000`.T],
        seqTimeEpochSeconds: Long Refined Interval.Closed[
          W.`0L`.T,
          W.`16025174999L`.T
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

      (parsed.isValid && parsed.exists {
        case x: SequenceNumberParts =>
          x.seqIndex == 0 &&
            x.shardCreateTime.getEpochSecond == shardCreateTime.getEpochSecond &&
            seqTime.getEpochSecond() == x.seqTime.getEpochSecond() &&
            x.shardIndex == shardIndex.value

        case _ => false
      }) :| s"shardCreateTime: ${shardCreateTime}\n" +
        s"shardIndex: ${shardIndex.value}\n" +
        s"seqTime: ${seqTime}\n" +
        s"Parsed: $parsed\n" +
        s"SequenceNumber: $sequenceNumber"
  })
}
