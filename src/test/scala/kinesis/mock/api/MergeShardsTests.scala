package kinesis.mock
package api

import cats.effect._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class MergeShardsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should merge shards")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(5, streamArn)
      val active =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )
      val shards = active.streams(streamArn).shards.keys.toVector

      val shardToMerge = shards.head
      val adjacentShardToMerge = shards(1)

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        req = MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId.shardId,
          streamArn.streamName
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
      val streams =
        Streams.empty.addStream(5, streamArn)

      val shards = streams.streams(streamArn).shards.keys.toVector

      val shardToMerge = shards.head
      val adjacentShardToMerge = shards(1)

      val req =
        MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId.shardId,
          streamArn.streamName
        )

      for {
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
      val streams =
        Streams.empty.addStream(5, streamArn)
      val active = streams.findAndUpdateStream(streamArn)(s =>
        s.copy(streamStatus = StreamStatus.ACTIVE)
      )
      val shards = active.streams(streamArn).shards.keys.toVector
      val shardToMerge = shards.head
      val adjacentShardToMerge =
        ShardId.create(shards.map(_.shardId.index).max + 1)

      val req =
        MergeShardsRequest(
          adjacentShardToMerge.shardId,
          shardToMerge.shardId.shardId,
          streamArn.streamName
        )

      for {
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
      val streams =
        Streams.empty.addStream(5, streamArn)
      val active = streams.findAndUpdateStream(streamArn)(s =>
        s.copy(streamStatus = StreamStatus.ACTIVE)
      )
      val shards = active.streams(streamArn).shards.keys.toVector
      val shardToMerge =
        ShardId.create(shards.map(_.shardId.index).max + 1)

      val adjacentShardToMerge = shards(1)

      val req =
        MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId,
          streamArn.streamName
        )

      for {
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
      val streams =
        Streams.empty.addStream(5, streamArn)
      val active = streams.findAndUpdateStream(streamArn)(s =>
        s.copy(streamStatus = StreamStatus.ACTIVE)
      )
      val shards = streams.streams(streamArn).shards.keys.toVector
      val shardToMerge = shards.head
      val adjacentShardToMerge = shards(2)

      val req =
        MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId.shardId,
          streamArn.streamName
        )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req
          .mergeShards(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
      } yield assert(
        res.isLeft,
        s"req: $req\nres: $res"
      )
  })
}
