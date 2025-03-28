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

import cats.effect._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models._

class MergeShardsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should merge shards")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(5, streamArn, None, now)
        active =
          streams.findAndUpdateStream(streamArn)(s =>
            s.copy(streamStatus = StreamStatus.ACTIVE)
          )
        shards = active.streams(streamArn).shards.keys.toVector
        shardToMerge = shards.head
        adjacentShardToMerge = shards(1)
        streamsRef <- Ref.of[IO, Streams](active)
        req = MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId.shardId,
          None,
          Some(streamArn)
        )
        res <- req.mergeShards(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.shards.keys.toVector.exists(shard =>
            shard.adjacentParentShardId
              .contains(adjacentShardToMerge.shardId.shardId) &&
              shard.parentShardId.contains(shardToMerge.shardId.shardId)
          ) && stream.streamStatus == StreamStatus.UPDATING
        },
        s"req: $req\n" +
          s"resShards: ${s.streams(streamArn).shards.keys.map(_.shardId)}\n" +
          s"parentHashKeyRange:${shardToMerge.hashKeyRange}\n" +
          s"adjacentHashKeyRange:${adjacentShardToMerge.hashKeyRange}"
      )
  })

  test("It should reject if the stream is inactive")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(5, streamArn, None, now)
        shards = streams.streams(streamArn).shards.keys.toVector
        shardToMerge = shards.head
        adjacentShardToMerge = shards(1)
        req = MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId.shardId,
          None,
          Some(streamArn)
        )
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req
          .mergeShards(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the adjacent shard is not found")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(5, streamArn, None, now)
        active = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )
        shards = active.streams(streamArn).shards.keys.toVector
        shardToMerge = shards.head
        adjacentShardToMerge =
          ShardId.create(shards.map(_.shardId.index).max + 1)
        req = MergeShardsRequest(
          adjacentShardToMerge.shardId,
          shardToMerge.shardId.shardId,
          None,
          Some(streamArn)
        )
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req
          .mergeShards(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the shard is not found")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(5, streamArn, None, now)
        active = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )
        shards = active.streams(streamArn).shards.keys.toVector
        shardToMerge =
          ShardId.create(shards.map(_.shardId.index).max + 1)
        adjacentShardToMerge = shards(1)
        req = MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId,
          None,
          Some(streamArn)
        )
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req
          .mergeShards(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the shards are not adjacent")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      for {
        now <- Utils.now
        streams = Streams.empty.addStream(5, streamArn, None, now)
        active = streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )
        shards = streams.streams(streamArn).shards.keys.toVector
        shardToMerge = shards.head
        adjacentShardToMerge = shards(2)
        req = MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId.shardId,
          None,
          Some(streamArn)
        )
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req
          .mergeShards(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })
}
