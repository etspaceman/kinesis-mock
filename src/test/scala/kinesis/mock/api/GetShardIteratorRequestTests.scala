package kinesis.mock
package api

import scala.collection.SortedMap

import java.time.Instant

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class GetShardIteratorRequestTests extends munit.ScalaCheckSuite {
  property("It should get a shard iterator for TRIM_HORIZON")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val shard = streams.streams(streamName).shards.head._1

      val records: List[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(50).toList.zipWithIndex.map {
          case (record, index) =>
            record.copy(sequenceNumber =
              SequenceNumber.create(
                shard.createdAtTimestamp,
                shard.shardId.index,
                None,
                Some(index),
                Some(record.approximateArrivalTimestamp)
              )
            )
        }

      val withRecords = streams.findAndUpdateStream(streamName) { s =>
        s.copy(
          shards = SortedMap(s.shards.head._1 -> records),
          streamStatus = StreamStatus.ACTIVE
        )
      }

      val req = GetShardIteratorRequest(
        shard.shardId.shardId,
        ShardIteratorType.TRIM_HORIZON,
        None,
        streamName,
        None
      )
      val res = req.getShardIterator(withRecords)
      val parsed = res.andThen(_.shardIterator.parse)

      (parsed.isValid && parsed.exists { parts =>
        parts.sequenceNumber == shard.sequenceNumberRange.startingSequenceNumber &&
        parts.shardId == shard.shardId.shardId &&
        parts.streamName == streamName
      }) :| s"req: $req\n" +
        s"res: $parsed\n"
  })

  property("It should get a shard iterator for LATEST")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val shard = streams.streams(streamName).shards.head._1

      val records: List[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(50).toList.zipWithIndex.map {
          case (record, index) =>
            record.copy(sequenceNumber =
              SequenceNumber.create(
                shard.createdAtTimestamp,
                shard.shardId.index,
                None,
                Some(index),
                Some(record.approximateArrivalTimestamp)
              )
            )
        }

      val withRecords = streams.findAndUpdateStream(streamName) { s =>
        s.copy(
          shards = SortedMap(s.shards.head._1 -> records),
          streamStatus = StreamStatus.ACTIVE
        )
      }

      val req = GetShardIteratorRequest(
        shard.shardId.shardId,
        ShardIteratorType.LATEST,
        None,
        streamName,
        None
      )
      val res = req.getShardIterator(withRecords)
      val parsed = res.andThen(_.shardIterator.parse)

      (parsed.isValid && parsed.exists { parts =>
        parts.sequenceNumber == records.last.sequenceNumber &&
        parts.shardId == shard.shardId.shardId &&
        parts.streamName == streamName
      }) :| s"req: $req\n" +
        s"res: $parsed\n"
  })

  property("It should get a shard iterator for AT_TIMESTAMP")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val shard = streams.streams(streamName).shards.head._1

      val startingInstant = Instant.parse("2021-01-01T00:00:00.00Z")

      val records: List[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(50).toList.zipWithIndex.map {
          case (record, index) =>
            val newTimestamp = startingInstant.plusSeconds(index.toLong)
            record.copy(
              approximateArrivalTimestamp = newTimestamp,
              sequenceNumber = SequenceNumber.create(
                shard.createdAtTimestamp,
                shard.shardId.index,
                None,
                Some(index),
                Some(newTimestamp)
              )
            )
        }

      val withRecords = streams.findAndUpdateStream(streamName) { s =>
        s.copy(
          shards = SortedMap(s.shards.head._1 -> records),
          streamStatus = StreamStatus.ACTIVE
        )
      }

      val req = GetShardIteratorRequest(
        shard.shardId.shardId,
        ShardIteratorType.AT_TIMESTAMP,
        None,
        streamName,
        Some(startingInstant.plusSeconds(25L))
      )

      val res = req.getShardIterator(withRecords)
      val parsed = res.andThen(_.shardIterator.parse)

      (parsed.isValid && parsed.exists { parts =>
        parts.sequenceNumber == records(24).sequenceNumber &&
        parts.shardId == shard.shardId.shardId &&
        parts.streamName == streamName
      }) :| s"req: $req\n" +
        s"res: $res\n" +
        s"parsed: $parsed\n" +
        s"sequenceIndexInRecords: ${parsed.map(parts => records.indexWhere(r => r.sequenceNumber == parts.sequenceNumber))}\n" +
        s"startingInstant: $startingInstant"
  })

  property("It should get a shard iterator for AT_SEQUENCE_NUMBER")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val shard = streams.streams(streamName).shards.head._1

      val records: List[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(50).toList.zipWithIndex.map {
          case (record, index) =>
            record.copy(sequenceNumber =
              SequenceNumber.create(
                shard.createdAtTimestamp,
                shard.shardId.index,
                None,
                Some(index),
                Some(record.approximateArrivalTimestamp)
              )
            )
        }

      val withRecords = streams.findAndUpdateStream(streamName) { s =>
        s.copy(
          shards = SortedMap(s.shards.head._1 -> records),
          streamStatus = StreamStatus.ACTIVE
        )
      }

      val req = GetShardIteratorRequest(
        shard.shardId.shardId,
        ShardIteratorType.AT_SEQUENCE_NUMBER,
        Some(records(25).sequenceNumber),
        streamName,
        None
      )
      val res = req.getShardIterator(withRecords)
      val parsed = res.andThen(_.shardIterator.parse)

      (parsed.isValid && parsed.exists { parts =>
        parts.sequenceNumber == records(24).sequenceNumber &&
        parts.shardId == shard.shardId.shardId &&
        parts.streamName == streamName
      }) :| s"req: $req\n" +
        s"res: $parsed\n"
  })

  property("It should get a shard iterator for AFTER_SEQUENCE_NUMBER")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val shard = streams.streams(streamName).shards.head._1

      val records: List[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(50).toList.zipWithIndex.map {
          case (record, index) =>
            record.copy(sequenceNumber =
              SequenceNumber.create(
                shard.createdAtTimestamp,
                shard.shardId.index,
                None,
                Some(index),
                Some(record.approximateArrivalTimestamp)
              )
            )
        }

      val withRecords = streams.findAndUpdateStream(streamName) { s =>
        s.copy(
          shards = SortedMap(s.shards.head._1 -> records),
          streamStatus = StreamStatus.ACTIVE
        )
      }

      val req = GetShardIteratorRequest(
        shard.shardId.shardId,
        ShardIteratorType.AFTER_SEQUENCE_NUMBER,
        Some(records(25).sequenceNumber),
        streamName,
        None
      )
      val res = req.getShardIterator(withRecords)
      val parsed = res.andThen(_.shardIterator.parse)

      (parsed.isValid && parsed.exists { parts =>
        parts.sequenceNumber == records(25).sequenceNumber &&
        parts.shardId == shard.shardId.shardId &&
        parts.streamName == streamName
      }) :| s"req: $req\n" +
        s"res: $parsed\n"
  })

  property("It should reject for AT_TIMESTAMP with no timestamp")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val shard = streams.streams(streamName).shards.head._1

      val req = GetShardIteratorRequest(
        shard.shardId.shardId,
        ShardIteratorType.AT_TIMESTAMP,
        None,
        streamName,
        None
      )
      val res = req.getShardIterator(streams)

      res.isInvalid :| s"req: $req\n" +
        s"res: $res\n"
  })

  property("It should reject for AT_TIMESTAMP with a timestamp in the future")(
    forAll {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val (streams, _) =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val shard = streams.streams(streamName).shards.head._1

        val req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.AT_TIMESTAMP,
          None,
          streamName,
          Some(Instant.now().plusSeconds(30))
        )
        val res = req.getShardIterator(streams)

        res.isInvalid :| s"req: $req\n" +
          s"res: $res\n"
    }
  )

  property("It should reject for AT_SEQUENCE_NUMBER with no sequenceNumber")(
    forAll {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val (streams, _) =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val shard = streams.streams(streamName).shards.head._1

        val req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.AT_SEQUENCE_NUMBER,
          None,
          streamName,
          None
        )
        val res = req.getShardIterator(streams)

        res.isInvalid :| s"req: $req\n" +
          s"res: $res\n"
    }
  )

  property("It should reject for AFTER_SEQUENCE_NUMBER with no sequenceNumber")(
    forAll {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val (streams, _) =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val shard = streams.streams(streamName).shards.head._1

        val req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.AT_SEQUENCE_NUMBER,
          None,
          streamName,
          None
        )
        val res = req.getShardIterator(streams)

        res.isInvalid :| s"req: $req\n" +
          s"res: $res\n"
    }
  )

}
