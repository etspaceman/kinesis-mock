package kinesis.mock.models

import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._

class ShardIteratorTests extends munit.ScalaCheckSuite {
  property("It should createt and parse correctly")(forAll {
    (
        streamName: StreamName,
        shardId: ShardId,
        sequenceNumber: SequenceNumber
    ) =>
      val iterator: ShardIterator = ShardIterator.create(
        streamName,
        shardId.shardId,
        sequenceNumber
      )
      val parsed = iterator.parse

      parsed.exists { parts =>
        parts.sequenceNumber == sequenceNumber &&
        parts.shardId == shardId.shardId &&
        parts.streamName == streamName
      } :| s"streamName: $streamName\n" +
        s"shardId: $shardId\n" +
        s"sequenceNumber: $sequenceNumber\n" +
        s"shardIterator: $iterator\n" +
        s"parsed: $parsed"
  })
}
