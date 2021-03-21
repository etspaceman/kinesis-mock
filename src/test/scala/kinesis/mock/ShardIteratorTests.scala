package kinesis.mock

import org.scalacheck.Prop._
import org.scalacheck.{Arbitrary, Gen}

import kinesis.mock.api.ShardIterator
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

case class StreamName(value: String)
object StreamName {
  implicit val streamNameArb: Arbitrary[StreamName] = Arbitrary(
    streamNameGen.map(StreamName.apply)
  )
}

case class ShardId(value: String)
object ShardId {
  implicit val shardIdArb: Arbitrary[ShardId] = Arbitrary(
    Gen.choose(0, 1000).map(Shard.shardId).map(ShardId.apply)
  )
}

class ShardIteratorTests extends munit.ScalaCheckSuite {
  property("It should createt and parse correctly")(forAll {
    (
        streamName: StreamName,
        shardId: ShardId,
        sequenceNumber: SequenceNumber
    ) =>
      val iterator: ShardIterator = ShardIterator.create(
        streamName.value,
        shardId.value,
        sequenceNumber
      )
      val parsed = iterator.parse

      parsed.exists { parts =>
        parts.sequenceNumber == sequenceNumber &&
        parts.shardId == shardId.value &&
        parts.streamName == streamName.value
      } :| s"streamName: ${streamName.value}\n" +
        s"shardId: ${shardId.value}\n" +
        s"sequenceNumber: ${sequenceNumber}\n" +
        s"shardIterator: $iterator\n" +
        s"parsed: $parsed"
  })
}
