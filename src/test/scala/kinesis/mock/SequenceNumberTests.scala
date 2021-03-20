package kinesis.mock

import scala.util.Random

import java.time.Instant

import kinesis.mock.models._

class SequenceNumberTests extends munit.FunSuite {
  test("It should create and parse correctly") {
    val shardCreateTime = Instant.now()
    val shardIndex = Random.between(0, 1000)
    val seqIndex = Some(Random.between(0, 1000))
    val seqTime = Some(Instant.now().plusSeconds(5))

    val sequenceNumber =
      SequenceNumber.create(
        shardCreateTime,
        shardIndex,
        None,
        seqIndex,
        seqTime
      )

    val parsed = sequenceNumber.parse

    assert(
      parsed.isValid && parsed.exists {
        case x: SequenceNumberParts =>
          seqIndex.contains(x.seqIndex) &&
            x.shardCreateTime.getEpochSecond == shardCreateTime.getEpochSecond &&
            seqTime.exists(y =>
              y.getEpochSecond() == x.seqTime.getEpochSecond()
            ) &&
            x.shardIndex == shardIndex

        case _ => false
      },
      s"shardCreateTime: $shardCreateTime\n" +
        s"shardIndex: $shardIndex\n" +
        s"seqIndex: $seqIndex\n" +
        s"seqTime: $seqTime\n" +
        s"Parsed: $parsed\n" +
        s"SequenceNumber: $sequenceNumber"
    )
  }

  test("It should substitute shardCreateTime for seqTime") {
    val shardCreateTime = Instant.now()
    val shardIndex = Random.between(0, 1000)
    val seqIndex = Some(Random.between(0, 1000))

    val sequenceNumber =
      SequenceNumber.create(
        shardCreateTime,
        shardIndex,
        None,
        seqIndex,
        None
      )

    val parsed = sequenceNumber.parse

    assert(
      parsed.isValid && parsed.exists {
        case x: SequenceNumberParts =>
          seqIndex.contains(x.seqIndex) &&
            x.shardCreateTime.getEpochSecond == shardCreateTime.getEpochSecond &&
            x.seqTime.getEpochSecond == shardCreateTime.getEpochSecond &&
            x.shardIndex == shardIndex

        case _ => false
      },
      s"shardCreateTime: $shardCreateTime\n" +
        s"shardIndex: $shardIndex\n" +
        s"seqIndex: $seqIndex\n" +
        s"Parsed: $parsed\n" +
        s"SequenceNumber: $sequenceNumber"
    )
  }

  test("It should substitute 0 for seqIndex") {
    val shardCreateTime = Instant.now()
    val shardIndex = Random.between(0, 1000)

    val sequenceNumber =
      SequenceNumber.create(
        shardCreateTime,
        shardIndex,
        None,
        None,
        None
      )

    val parsed = sequenceNumber.parse

    assert(
      parsed.isValid && parsed.exists {
        case x: SequenceNumberParts =>
          x.seqIndex == 0 &&
            x.shardCreateTime.getEpochSecond == shardCreateTime.getEpochSecond &&
            x.seqTime.getEpochSecond == shardCreateTime.getEpochSecond &&
            x.shardIndex == shardIndex

        case _ => false
      },
      s"shardCreateTime: $shardCreateTime\n" +
        s"shardIndex: $shardIndex\n" +
        s"Parsed: $parsed\n" +
        s"SequenceNumber: $sequenceNumber"
    )
  }
}
