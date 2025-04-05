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

import cats.effect.*
import org.scalacheck.Gen
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models.*
import kinesis.mock.syntax.scalacheck.*

class SplitShardTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite:
  test("It should split shards")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(5, streamArn, None, now)
        active = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )
        shardToSplit = active.streams(streamArn).shards.keys.head
        newStartingHashKey = Gen
          .choose(
            shardToSplit.hashKeyRange.startingHashKey + BigInt(1),
            shardToSplit.hashKeyRange.endingHashKey - BigInt(1)
          )
          .one
          .toString
        req = SplitShardRequest(
          newStartingHashKey,
          shardToSplit.shardId.shardId,
          None,
          Some(streamArn)
        )
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.splitShard(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.shards.keys.toVector.count(shard =>
            shard.parentShardId.contains(shardToSplit.shardId.shardId)
          ) == 2 && stream.streamStatus == StreamStatus.UPDATING
        },
        s"req: $req\n" +
          s"resShards: ${s.streams(streamArn).shards.keys.map(_.shardId)}"
      )
  })

  test("It should reject if the stream is inactive")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(5, streamArn, None, now)
        shardToSplit = streams.streams(streamArn).shards.keys.head
        newStartingHashKey = Gen
          .choose(
            shardToSplit.hashKeyRange.startingHashKey + BigInt(1),
            shardToSplit.hashKeyRange.endingHashKey - BigInt(1)
          )
          .one
          .toString
        req = SplitShardRequest(
          newStartingHashKey,
          shardToSplit.shardId.shardId,
          None,
          Some(streamArn)
        )
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.splitShard(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject if the shard is not found")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for
        now <- Utils.now
        streams = Streams.empty.addStream(5, streamArn, None, now)
        active = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )
        shardToSplit = ShardId.create(
          active
            .streams(streamArn)
            .shards
            .keys
            .toVector
            .map(_.shardId.index)
            .max + 1
        )
        req = SplitShardRequest(
          "0",
          shardToSplit.shardId,
          None,
          Some(streamArn)
        )
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.splitShard(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject if the operation would exceed the shard limit")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        for
          now <- Utils.now
          streams = Streams.empty.addStream(5, streamArn, None, now)
          active = streams.findAndUpdateStream(streamArn)(s =>
            s.copy(streamStatus = StreamStatus.ACTIVE)
          )
          shardToSplit = active.streams(streamArn).shards.keys.head
          newStartingHashKey = Gen
            .choose(
              shardToSplit.hashKeyRange.startingHashKey + BigInt(1),
              shardToSplit.hashKeyRange.endingHashKey - BigInt(1)
            )
            .one
            .toString
          req = SplitShardRequest(
            newStartingHashKey,
            shardToSplit.shardId.shardId,
            None,
            Some(streamArn)
          )
          streamsRef <- Ref.of[IO, Streams](active)
          res <- req.splitShard(
            streamsRef,
            5,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
        yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )
