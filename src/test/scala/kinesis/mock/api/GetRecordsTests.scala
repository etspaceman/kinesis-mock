package kinesis.mock
package api

import scala.collection.SortedMap

import cats.effect.{IO, Ref}
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class GetRecordsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should get records")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val shard = streams.streams(streamArn).shards.head._1

      val records: Vector[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(100).toVector.zipWithIndex.map {
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

      val withRecords = streams.findAndUpdateStream(streamArn) { s =>
        s.copy(
          shards = SortedMap(s.shards.head._1 -> records),
          streamStatus = StreamStatus.ACTIVE
        )
      }

      val shardIterator = ShardIterator.create(
        streamArn.streamName,
        shard.shardId.shardId,
        shard.sequenceNumberRange.startingSequenceNumber
      )

      for {
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetRecordsRequest(None, shardIterator, None)
        res <- req.getRecords(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          response.records.toVector === records
        },
        s"req: $req\n" +
          s"resCount: ${res.map(_.records.length)}\n" +
          s"resHead: ${res.map(r => r.records.head).fold(_.toString, _.toString)}\n" +
          s"recHead: ${records.head}"
      )
  })

  test("It should get records with a limit")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val shard = streams.streams(streamArn).shards.head._1

      val records: Vector[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(100).toVector.zipWithIndex.map {
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

      val withRecords = streams.findAndUpdateStream(streamArn) { s =>
        s.copy(
          shards = SortedMap(s.shards.head._1 -> records),
          streamStatus = StreamStatus.ACTIVE
        )
      }

      val shardIterator = ShardIterator.create(
        streamArn.streamName,
        shard.shardId.shardId,
        shard.sequenceNumberRange.startingSequenceNumber
      )

      for {
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetRecordsRequest(Some(50), shardIterator, None)
        res <- req.getRecords(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          response.records.toVector === records.take(50)
        },
        s"req: $req\n" +
          s"resCount: ${res.map(_.records.length)}\n" +
          s"resHead: ${res.map(r => r.records.head).fold(_.toString, _.toString)}\n" +
          s"recHead: ${records.head}"
      )
  })

  test("It should get records using the new shard-iterator")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val shard = streams.streams(streamArn).shards.head._1

      val records: Vector[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(100).toVector.zipWithIndex.map {
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

      val withRecords = streams.findAndUpdateStream(streamArn) { s =>
        s.copy(
          shards = SortedMap(s.shards.head._1 -> records),
          streamStatus = StreamStatus.ACTIVE
        )
      }

      val shardIterator = ShardIterator.create(
        streamArn.streamName,
        shard.shardId.shardId,
        shard.sequenceNumberRange.startingSequenceNumber
      )

      for {
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req1 = GetRecordsRequest(Some(50), shardIterator, None)
        res1 <- req1.getRecords(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        res2 <- res1
          .traverse(r =>
            GetRecordsRequest(Some(50), r.nextShardIterator.orNull, None)
              .getRecords(
                streamsRef,
                streamArn.awsRegion,
                streamArn.awsAccountId
              )
          )
          .map(_.flatMap(identity))
        res = res1.flatMap(r1 => res2.map(r2 => (r1, r2)))
      } yield assert(
        res.isRight && res.exists { case (r1, r2) =>
          r1.records.toVector === records
            .take(50) && r2.records.toVector === records
            .takeRight(50)
        },
        s"res1Head: ${res.map { case (r1, _) => r1.records.head }.fold(_.toString, _.toString)}\n" +
          s"recHead: ${records.head}\n" +
          s"res2Head: ${res.map { case (_, r2) => r2.records.head }.fold(_.toString, _.toString)}\n" +
          s"rec51: ${records(50)}\n" +
          s"res2HeadInex: ${res
              .map { case (_, r2) =>
                records.indexWhere(_.partitionKey == r2.records.head.partitionKey)
              }
              .fold(_.toString, _.toString)}"
      )
  })

  test(
    "It should return an empty list using the new shard-iterator if the stream is exhausted"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val shard = streams.streams(streamArn).shards.head._1

      val records: Vector[KinesisRecord] =
        kinesisRecordArbitrary.arbitrary.take(50).toVector.zipWithIndex.map {
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

      val withRecords = streams.findAndUpdateStream(streamArn) { s =>
        s.copy(
          shards = SortedMap(s.shards.head._1 -> records),
          streamStatus = StreamStatus.ACTIVE
        )
      }

      val shardIterator = ShardIterator.create(
        streamArn.streamName,
        shard.shardId.shardId,
        shard.sequenceNumberRange.startingSequenceNumber
      )

      for {
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req1 = GetRecordsRequest(Some(50), shardIterator, None)
        res1 <- req1.getRecords(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        res2 <- res1
          .traverse(r =>
            GetRecordsRequest(Some(50), r.nextShardIterator.orNull, None)
              .getRecords(
                streamsRef,
                streamArn.awsRegion,
                streamArn.awsAccountId
              )
          )
          .map(_.flatMap(identity))
        res = res1.flatMap(r1 => res2.map(r2 => (r1, r2)))
      } yield assert(
        res.isRight && res.exists { case (r1, r2) =>
          r1.records.toVector === records
            .take(50) && r2.records.isEmpty
        },
        s"res1Head: ${res.map { case (r1, _) => r1.records.head }.fold(_.toString, _.toString)}\n" +
          s"recHead: ${records.head}"
      )
  })
}
