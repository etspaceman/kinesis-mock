package kinesis.mock
package api

import scala.collection.SortedMap

import java.util.Base64

import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class GetRecordsTests extends munit.ScalaCheckSuite {
  property("It should get records")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val shard = streams.streams(streamName).shards.head._1

      val records: List[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(100).toList.zipWithIndex.map {
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

      val shardIterator = ShardIterator.create(
        streamName,
        shard.shardId.shardId,
        shard.sequenceNumberRange.startingSequenceNumber
      )

      val req = GetRecordsRequest(None, shardIterator)
      val res = req.getRecords(withRecords)

      (res.isValid && res.exists { response =>
        response.records === records.map(r =>
          r.copy(data = Base64.getEncoder().encode(r.data))
        )
      }) :| s"req: $req\n" +
        s"resCount: ${res.map(_.records.length)}\n" +
        s"resHead: ${res.map(r => r.records.head).fold(_.toString, _.toString)}\n" +
        s"recHead: ${records.head}"
  })

  property("It should get records with a limit")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val shard = streams.streams(streamName).shards.head._1

      val records: List[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(100).toList.zipWithIndex.map {
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

      val shardIterator = ShardIterator.create(
        streamName,
        shard.shardId.shardId,
        shard.sequenceNumberRange.startingSequenceNumber
      )

      val req = GetRecordsRequest(Some(50), shardIterator)
      val res = req.getRecords(withRecords)

      (res.isValid && res.exists { response =>
        response.records === records
          .take(50)
          .map(r => r.copy(data = Base64.getEncoder().encode(r.data)))
      }) :| s"req: $req\n" +
        s"resCount: ${res.map(_.records.length)}\n" +
        s"resHead: ${res.map(r => r.records.head).fold(_.toString, _.toString)}\n" +
        s"recHead: ${records.head}"
  })

  property("It should get records using the new shard-iterator")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val shard = streams.streams(streamName).shards.head._1

      val records: List[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(100).toList.zipWithIndex.map {
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

      val shardIterator = ShardIterator.create(
        streamName,
        shard.shardId.shardId,
        shard.sequenceNumberRange.startingSequenceNumber
      )

      val req1 = GetRecordsRequest(Some(50), shardIterator)
      val res = req1.getRecords(withRecords).andThen { res1 =>
        GetRecordsRequest(Some(50), res1.nextShardIterator)
          .getRecords(withRecords)
          .map(res2 => (res1, res2))
      }

      (res.isValid && res.exists { case (res1, res2) =>
        res1.records === records
          .take(50)
          .map(r =>
            r.copy(data = Base64.getEncoder().encode(r.data))
          ) && res2.records === records
          .takeRight(50)
          .map(r => r.copy(data = Base64.getEncoder().encode(r.data)))
      }) :|
        s"res1Head: ${res.map { case (res1, _) => res1.records.head }.fold(_.toString, _.toString)}\n" +
        s"recHead: ${records.head}\n" +
        s"res2Head: ${res.map { case (_, res2) => res2.records.head }.fold(_.toString, _.toString)}\n" +
        s"rec51: ${records(50)}\n" +
        s"res2HeadInex: ${res
          .map { case (_, res2) =>
            records.indexWhere(_.partitionKey == res2.records.head.partitionKey)
          }
          .fold(_.toString, _.toString)}"
  })

  property(
    "It should return an empty list using the new shard-iterator if the stream is exhausted"
  )(forAll {
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

      val shardIterator = ShardIterator.create(
        streamName,
        shard.shardId.shardId,
        shard.sequenceNumberRange.startingSequenceNumber
      )

      val req1 = GetRecordsRequest(Some(50), shardIterator)
      val res = req1.getRecords(withRecords).andThen { res1 =>
        GetRecordsRequest(Some(50), res1.nextShardIterator)
          .getRecords(withRecords)
          .map(res2 => (res1, res2))
      }

      (res.isValid && res.exists { case (res1, res2) =>
        res1.records === records
          .take(50)
          .map(r =>
            r.copy(data = Base64.getEncoder().encode(r.data))
          ) && res2.records.isEmpty
      }) :|
        s"res1Head: ${res.map { case (res1, _) => res1.records.head }.fold(_.toString, _.toString)}\n" +
        s"recHead: ${records.head}"
  })
}
