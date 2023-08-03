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

package kinesis.mock
package api

import scala.collection.SortedMap

import java.time.Instant

import cats.effect.{IO, Ref}
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
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        shard = streams.streams(streamArn).shards.head._1
        records = kinesisRecordArbitrary.arbitrary
          .take(50)
          .toVector
          .zipWithIndex
          .map { case (record, index) =>
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
        withRecords = streams.findAndUpdateStream(streamArn) { s =>
          s.copy(
            shards = SortedMap(s.shards.head._1 -> records),
            streamStatus = StreamStatus.ACTIVE
          )
        }
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.TRIM_HORIZON,
          None,
          None,
          Some(streamArn),
          None
        )
        res <- req.getShardIterator(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        parsed = res.flatMap(_.shardIterator.parse(now.plusSeconds(2)))
      } yield assert(
        parsed.isRight && parsed.exists { parts =>
          parts.sequenceNumber == shard.sequenceNumberRange.startingSequenceNumber &&
          parts.shardId == shard.shardId.shardId &&
          parts.streamName == streamArn.streamName
        },
        s"req: $req\nres: $parsed\n"
      )
  })

  test("It should get a shard iterator for LATEST")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        shard = streams.streams(streamArn).shards.head._1
        records = kinesisRecordArbitrary.arbitrary
          .take(50)
          .toVector
          .zipWithIndex
          .map { case (record, index) =>
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
        withRecords = streams.findAndUpdateStream(streamArn) { s =>
          s.copy(
            shards = SortedMap(s.shards.head._1 -> records),
            streamStatus = StreamStatus.ACTIVE
          )
        }
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.LATEST,
          None,
          None,
          Some(streamArn),
          None
        )
        res <- req.getShardIterator(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        parsed = res.flatMap(_.shardIterator.parse(now.plusSeconds(2)))
      } yield assert(
        parsed.isRight && parsed.exists { parts =>
          parts.sequenceNumber == records.last.sequenceNumber &&
          parts.shardId == shard.shardId.shardId &&
          parts.streamName == streamArn.streamName
        },
        s"req: $req\nres: $parsed\n"
      )
  })

  test("It should get a shard iterator for AT_TIMESTAMP")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        shard = streams.streams(streamArn).shards.head._1
        startingInstant = Instant.parse("2021-01-01T00:00:00.00Z")
        records =
          kinesisRecordArbitrary.arbitrary.take(50).toVector.zipWithIndex.map {
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
        withRecords = streams.findAndUpdateStream(streamArn) { s =>
          s.copy(
            shards = SortedMap(s.shards.head._1 -> records),
            streamStatus = StreamStatus.ACTIVE
          )
        }
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.AT_TIMESTAMP,
          None,
          None,
          Some(streamArn),
          Some(startingInstant.plusSeconds(25L))
        )
        res <- req.getShardIterator(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        parsed = res.flatMap(_.shardIterator.parse(now.plusSeconds(2)))
      } yield assert(
        parsed.isRight && parsed.exists { parts =>
          parts.sequenceNumber == records(24).sequenceNumber &&
          parts.shardId == shard.shardId.shardId &&
          parts.streamName == streamArn.streamName
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
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        shard = streams.streams(streamArn).shards.head._1
        records = kinesisRecordArbitrary.arbitrary
          .take(50)
          .toVector
          .zipWithIndex
          .map { case (record, index) =>
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
        withRecords = streams.findAndUpdateStream(streamArn) { s =>
          s.copy(
            shards = SortedMap(s.shards.head._1 -> records),
            streamStatus = StreamStatus.ACTIVE
          )
        }
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.AT_SEQUENCE_NUMBER,
          Some(records(25).sequenceNumber),
          None,
          Some(streamArn),
          None
        )
        res <- req.getShardIterator(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        parsed = res.flatMap(_.shardIterator.parse(now.plusSeconds(2)))
      } yield assert(
        parsed.isRight && parsed.exists { parts =>
          parts.sequenceNumber == records(24).sequenceNumber &&
          parts.shardId == shard.shardId.shardId &&
          parts.streamName == streamArn.streamName
        },
        s"req: $req\nres: $parsed\n"
      )
  })

  test(
    "It should get a shard iterator for AT_SEQUENCE_NUMBER if the sequence number is the beginning of the shard"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        shard = streams.streams(streamArn).shards.head._1
        records = kinesisRecordArbitrary.arbitrary
          .take(50)
          .toVector
          .zipWithIndex
          .map { case (record, index) =>
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
        withRecords = streams.findAndUpdateStream(streamArn) { s =>
          s.copy(
            shards = SortedMap(s.shards.head._1 -> records),
            streamStatus = StreamStatus.ACTIVE
          )
        }
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.AT_SEQUENCE_NUMBER,
          Some(shard.sequenceNumberRange.startingSequenceNumber),
          None,
          Some(streamArn),
          None
        )
        res <- req.getShardIterator(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        parsed = res.flatMap(_.shardIterator.parse(now.plusSeconds(2)))
      } yield assert(
        parsed.isRight && parsed.exists { parts =>
          parts.sequenceNumber == shard.sequenceNumberRange.startingSequenceNumber &&
          parts.shardId == shard.shardId.shardId &&
          parts.streamName == streamArn.streamName
        },
        s"req: $req\nres: $parsed\n"
      )
  })

  test("It should get a shard iterator for AFTER_SEQUENCE_NUMBER")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        for {
          now <- Utils.now
          streams = Streams.empty.addStream(1, streamArn, None, now)
          shard = streams.streams(streamArn).shards.head._1
          records = kinesisRecordArbitrary.arbitrary
            .take(50)
            .toVector
            .zipWithIndex
            .map { case (record, index) =>
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
          withRecords = streams.findAndUpdateStream(streamArn) { s =>
            s.copy(
              shards = SortedMap(s.shards.head._1 -> records),
              streamStatus = StreamStatus.ACTIVE
            )
          }
          streamsRef <- Ref.of[IO, Streams](withRecords)
          req = GetShardIteratorRequest(
            shard.shardId.shardId,
            ShardIteratorType.AFTER_SEQUENCE_NUMBER,
            Some(records(25).sequenceNumber),
            None,
            Some(streamArn),
            None
          )
          res <- req.getShardIterator(
            streamsRef,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
          parsed = res.flatMap(_.shardIterator.parse(now.plusSeconds(2)))
        } yield assert(
          parsed.isRight && parsed.exists { parts =>
            parts.sequenceNumber == records(25).sequenceNumber &&
            parts.shardId == shard.shardId.shardId &&
            parts.streamName == streamArn.streamName
          },
          s"req: $req\nres: $parsed\n"
        )
    }
  )

  test(
    "It should get a shard iterator for AFTER_SEQUENCE_NUMBER if the sequence number is the beginning of the shard"
  )(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        for {
          now <- Utils.now
          streams = Streams.empty.addStream(1, streamArn, None, now)
          shard = streams.streams(streamArn).shards.head._1
          records = kinesisRecordArbitrary.arbitrary
            .take(50)
            .toVector
            .zipWithIndex
            .map { case (record, index) =>
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
          withRecords = streams.findAndUpdateStream(streamArn) { s =>
            s.copy(
              shards = SortedMap(s.shards.head._1 -> records),
              streamStatus = StreamStatus.ACTIVE
            )
          }
          streamsRef <- Ref.of[IO, Streams](withRecords)
          req = GetShardIteratorRequest(
            shard.shardId.shardId,
            ShardIteratorType.AFTER_SEQUENCE_NUMBER,
            Some(shard.sequenceNumberRange.startingSequenceNumber),
            None,
            Some(streamArn),
            None
          )
          res <- req.getShardIterator(
            streamsRef,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
          parsed = res.flatMap(_.shardIterator.parse(now.plusSeconds(2)))
        } yield assert(
          parsed.isRight && parsed.exists { parts =>
            parts.sequenceNumber == shard.sequenceNumberRange.startingSequenceNumber &&
            parts.shardId == shard.shardId.shardId &&
            parts.streamName == streamArn.streamName
          },
          s"req: $req\nres: $parsed\n"
        )
    }
  )

  test("It should reject for AT_TIMESTAMP with no timestamp")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        shard = streams.streams(streamArn).shards.head._1
        streamsRef <- Ref.of[IO, Streams](streams)
        req = GetShardIteratorRequest(
          shard.shardId.shardId,
          ShardIteratorType.AT_TIMESTAMP,
          None,
          None,
          Some(streamArn),
          None
        )
        res <- req.getShardIterator(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(res.isLeft, s"req: $req")
  })

  test("It should reject for AT_TIMESTAMP with a timestamp in the future")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        for {
          now <- Utils.now
          streams = Streams.empty.addStream(1, streamArn, None, now)
          shard = streams.streams(streamArn).shards.head._1
          streamsRef <- Ref.of[IO, Streams](streams)
          req = GetShardIteratorRequest(
            shard.shardId.shardId,
            ShardIteratorType.AT_TIMESTAMP,
            None,
            None,
            Some(streamArn),
            Some(now.plusSeconds(30))
          )
          res <- req.getShardIterator(
            streamsRef,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
        } yield assert(res.isLeft, s"req: $req")
    }
  )

  test("It should reject for AT_SEQUENCE_NUMBER with no sequenceNumber")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        for {
          now <- Utils.now
          streams = Streams.empty.addStream(1, streamArn, None, now)
          shard = streams.streams(streamArn).shards.head._1
          streamsRef <- Ref.of[IO, Streams](streams)
          req = GetShardIteratorRequest(
            shard.shardId.shardId,
            ShardIteratorType.AT_SEQUENCE_NUMBER,
            None,
            None,
            Some(streamArn),
            None
          )
          res <- req.getShardIterator(
            streamsRef,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
        } yield assert(res.isLeft, s"req: $req")
    }
  )

  test("It should reject for AFTER_SEQUENCE_NUMBER with no sequenceNumber")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        for {
          now <- Utils.now
          streams = Streams.empty.addStream(1, streamArn, None, now)
          shard = streams.streams(streamArn).shards.head._1
          streamsRef <- Ref.of[IO, Streams](streams)
          req = GetShardIteratorRequest(
            shard.shardId.shardId,
            ShardIteratorType.AT_SEQUENCE_NUMBER,
            None,
            None,
            Some(streamArn),
            None
          )
          res <- req.getShardIterator(
            streamsRef,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
        } yield assert(res.isLeft, s"req: $req")
    }
  )

}
