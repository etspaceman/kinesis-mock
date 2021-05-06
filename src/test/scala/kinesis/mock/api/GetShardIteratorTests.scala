package kinesis.mock
package api

import scala.collection.SortedMap

import java.time.Instant

import cats.effect.IO
import cats.effect.concurrent.Ref
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class GetShardIteratorTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should get a shard iterator for TRIM_HORIZON")(PropF.forAllF {
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

      for {
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.TRIM_HORIZON,
          None,
          streamName,
          None
        )
        res <- req.getShardIterator(streamsRef)
        parsed = res.andThen(_.shardIterator.parse)
      } yield assert(
        parsed.isValid && parsed.exists { parts =>
          parts.sequenceNumber == shard.sequenceNumberRange.startingSequenceNumber &&
          parts.shardId == shard.shardId.shardId &&
          parts.streamName == streamName
        },
        s"req: $req\nres: $parsed\n"
      )
  })

  test("It should get a shard iterator for LATEST")(PropF.forAllF {
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

      for {
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.LATEST,
          None,
          streamName,
          None
        )
        res <- req.getShardIterator(streamsRef)
        parsed = res.andThen(_.shardIterator.parse)
      } yield assert(
        parsed.isValid && parsed.exists { parts =>
          parts.sequenceNumber == records.last.sequenceNumber &&
          parts.shardId == shard.shardId.shardId &&
          parts.streamName == streamName
        },
        s"req: $req\nres: $parsed\n"
      )
  })

  test("It should get a shard iterator for AT_TIMESTAMP")(PropF.forAllF {
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

      for {
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.AT_TIMESTAMP,
          None,
          streamName,
          Some(startingInstant.plusSeconds(25L))
        )
        res <- req.getShardIterator(streamsRef)
        parsed = res.andThen(_.shardIterator.parse)
      } yield assert(
        parsed.isValid && parsed.exists { parts =>
          parts.sequenceNumber == records(24).sequenceNumber &&
          parts.shardId == shard.shardId.shardId &&
          parts.streamName == streamName
        },
        s"req: $req\n" +
          s"res: $res\n" +
          s"parsed: $parsed\n" +
          s"sequenceIndexInRecords: ${parsed.map(parts => records.indexWhere(r => r.sequenceNumber == parts.sequenceNumber))}\n" +
          s"startingInstant: $startingInstant"
      )
  })

  test("It should get a shard iterator for AT_SEQUENCE_NUMBER")(PropF.forAllF {
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

      for {
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.AT_SEQUENCE_NUMBER,
          Some(records(25).sequenceNumber),
          streamName,
          None
        )
        res <- req.getShardIterator(streamsRef)
        parsed = res.andThen(_.shardIterator.parse)
      } yield assert(
        parsed.isValid && parsed.exists { parts =>
          parts.sequenceNumber == records(24).sequenceNumber &&
          parts.shardId == shard.shardId.shardId &&
          parts.streamName == streamName
        },
        s"req: $req\nres: $parsed\n"
      )
  })

  test("It should get a shard iterator for AFTER_SEQUENCE_NUMBER")(
    PropF.forAllF {
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

        for {
          streamsRef <- Ref.of[IO, Streams](withRecords)
          req = GetShardIteratorRequest(
            shard.shardId.shardId,
            ShardIteratorType.AFTER_SEQUENCE_NUMBER,
            Some(records(25).sequenceNumber),
            streamName,
            None
          )
          res <- req.getShardIterator(streamsRef)
          parsed = res.andThen(_.shardIterator.parse)
        } yield assert(
          parsed.isValid && parsed.exists { parts =>
            parts.sequenceNumber == records(25).sequenceNumber &&
            parts.shardId == shard.shardId.shardId &&
            parts.streamName == streamName
          },
          s"req: $req\nres: $parsed\n"
        )
    }
  )

  test("It should reject for AT_TIMESTAMP with no timestamp")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val shard = streams.streams(streamName).shards.head._1

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.AT_TIMESTAMP,
          None,
          streamName,
          None
        )
        res <- req.getShardIterator(streamsRef)
      } yield assert(res.isInvalid, s"req: $req")
  })

  test("It should reject for AT_TIMESTAMP with a timestamp in the future")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val (streams, _) =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val shard = streams.streams(streamName).shards.head._1

        for {
          streamsRef <- Ref.of[IO, Streams](streams)
          req = GetShardIteratorRequest(
            shard.shardId.shardId,
            ShardIteratorType.AT_TIMESTAMP,
            None,
            streamName,
            Some(Instant.now().plusSeconds(30))
          )
          res <- req.getShardIterator(streamsRef)
        } yield assert(res.isInvalid, s"req: $req")
    }
  )

  test("It should reject for AT_SEQUENCE_NUMBER with no sequenceNumber")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val (streams, _) =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val shard = streams.streams(streamName).shards.head._1

        for {
          streamsRef <- Ref.of[IO, Streams](streams)
          req = GetShardIteratorRequest(
            shard.shardId.shardId,
            ShardIteratorType.AT_SEQUENCE_NUMBER,
            None,
            streamName,
            None
          )
          res <- req.getShardIterator(streamsRef)
        } yield assert(res.isInvalid, s"req: $req")
    }
  )

  test("It should reject for AFTER_SEQUENCE_NUMBER with no sequenceNumber")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val (streams, _) =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val shard = streams.streams(streamName).shards.head._1

        for {
          streamsRef <- Ref.of[IO, Streams](streams)
          req = GetShardIteratorRequest(
            shard.shardId.shardId,
            ShardIteratorType.AT_SEQUENCE_NUMBER,
            None,
            streamName,
            None
          )
          res <- req.getShardIterator(streamsRef)
        } yield assert(res.isInvalid, s"req: $req")
    }
  )

}
