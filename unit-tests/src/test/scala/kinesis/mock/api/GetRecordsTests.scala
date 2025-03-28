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

import cats.effect.{IO, Ref}
import cats.syntax.all._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class GetRecordsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should get records")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        shard = streams.streams(streamArn).shards.head._1
        records =
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
        withRecords = streams.findAndUpdateStream(streamArn) { s =>
          s.copy(
            shards = SortedMap(s.shards.head._1 -> records),
            streamStatus = StreamStatus.ACTIVE
          )
        }
        shardIterator = ShardIterator.create(
          streamArn.streamName,
          shard.shardId.shardId,
          shard.sequenceNumberRange.startingSequenceNumber,
          now
        )
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetRecordsRequest(None, shardIterator, None)
        res <- req.getRecords(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          response.records.toVector === records &&
          response.nextShardIterator.nonEmpty &&
          response.childShards.isEmpty
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
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        shard = streams.streams(streamArn).shards.head._1
        records = kinesisRecordArbitrary.arbitrary
          .take(100)
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
        shardIterator = ShardIterator.create(
          streamArn.streamName,
          shard.shardId.shardId,
          shard.sequenceNumberRange.startingSequenceNumber,
          now
        )
        streamsRef <- Ref.of[IO, Streams](withRecords)
        req = GetRecordsRequest(Some(50), shardIterator, None)
        res <- req.getRecords(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          response.records.toVector === records.take(50) &&
          response.nextShardIterator.nonEmpty &&
          response.childShards.isEmpty
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
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        shard = streams.streams(streamArn).shards.head._1
        records = kinesisRecordArbitrary.arbitrary
          .take(100)
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
        shardIterator = ShardIterator.create(
          streamArn.streamName,
          shard.shardId.shardId,
          shard.sequenceNumberRange.startingSequenceNumber,
          now
        )
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
            .takeRight(50) &&
          r2.nextShardIterator.nonEmpty &&
          r2.childShards.isEmpty
        },
        s"res1Head: ${res.map { case (r1, _) => r1.records.head }.fold(_.toString, _.toString)}\n" +
          s"recHead: ${records.head}\n" +
          s"res2Head: ${res.map { case (_, r2) => r2.records.head }.fold(_.toString, _.toString)}\n" +
          s"rec51: ${records(50)}\n" +
          s"res2HeadInex: ${res
              .map { case (_, r2) =>
                records.indexWhere(_.partitionKey == r2.records.head.partitionKey)
              }
              .fold(_.toString, _.toString)}\n" +
          s"res2NextShardIterator: ${res.map { case (_, r2) =>
              r2.nextShardIterator
            }}\n" +
          s"res2ChildShards: ${res.map { case (_, r2) => r2.childShards }}"
      )
  })

  test(
    "It should return an empty list using the new shard-iterator if the stream is exhausted"
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
        shardIterator = ShardIterator.create(
          streamArn.streamName,
          shard.shardId.shardId,
          shard.sequenceNumberRange.startingSequenceNumber,
          now
        )
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
            .take(50) && r2.records.isEmpty &&
          r2.nextShardIterator.nonEmpty &&
          r2.childShards.isEmpty
        },
        s"res1Head: ${res.map { case (r1, _) => r1.records.head }.fold(_.toString, _.toString)}\n" +
          s"recHead: ${records.head}"
      )
  })

  test("It should get records after a scaling operation")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(1, streamArn, None, now)
        shard = streams.streams(streamArn).shards.head._1
        records = kinesisRecordArbitrary.arbitrary
          .take(100)
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
        shardIterator = ShardIterator.create(
          streamArn.streamName,
          shard.shardId.shardId,
          shard.sequenceNumberRange.startingSequenceNumber,
          now
        )
        streamsRef <- Ref.of[IO, Streams](withRecords)
        scaleReq = UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          None,
          Some(streamArn),
          2
        )
        _ <- scaleReq.updateShardCount(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        _ <- streamsRef.update(
          _.findAndUpdateStream(streamArn)(s =>
            s.copy(streamStatus = StreamStatus.ACTIVE)
          )
        )
        req = GetRecordsRequest(None, shardIterator, None)
        res <- req.getRecords(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(
        res.isRight && res.exists { response =>
          response.records.toVector === records &&
          response.nextShardIterator.isEmpty &&
          response.childShards.exists(_.nonEmpty)
        },
        s"req: $req\n" +
          s"resCount: ${res.map(_.records.length)}\n" +
          s"resHead: ${res.map(r => r.records.head).fold(_.toString, _.toString)}\n" +
          s"recHead: ${records.head}"
      )
  })
}
