package kinesis.mock
package api

import cats.effect._
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import cats.effect.std.Semaphore

class MergeShardsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should merge shards")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, shardSemaphoreKeys) =
        Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)
      val active =
        streams.findAndUpdateStream(streamName)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )
      val shardToMerge =
        active.streams(streamName).shards.keys.head
      val adjacentShardToMerge =
        active.streams(streamName).shards.keys.toList(1)

      val req =
        MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId.shardId,
          streamName
        )

      for {
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        res <- req.mergeShards(active, shardSemaphores)
      } yield assert(
        res.isValid && res.exists { case (resultStreams, _) =>
          resultStreams.streams.get(streamName).exists { stream =>
            stream.shards.keys.toList.exists(shard =>
              shard.adjacentParentShardId
                .contains(adjacentShardToMerge.shardId.shardId) &&
                shard.parentShardId.contains(shardToMerge.shardId.shardId)
            ) && stream.streamStatus == StreamStatus.UPDATING
          }
        },
        s"req: $req\n" +
          s"resShards: ${res.map(_._1.streams(streamName).shards.keys.map(_.shardId))}\n" +
          s"parentHashKeyRange:${shardToMerge.hashKeyRange}\n" +
          s"adjacentHashKeyRange:${adjacentShardToMerge.hashKeyRange}"
      )
  })

  test("It should reject if the stream is inactive")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, shardSemaphoreKeys) =
        Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)

      val shardToMerge =
        streams.streams(streamName).shards.keys.head
      val adjacentShardToMerge =
        streams.streams(streamName).shards.keys.toList(1)

      val req =
        MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId.shardId,
          streamName
        )

      for {
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        res <- req.mergeShards(streams, shardSemaphores)
      } yield assert(
        res.isInvalid,
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the adjacent shard is not found")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, shardSemaphoreKeys) =
        Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)
      val active = streams.findAndUpdateStream(streamName)(s =>
        s.copy(streamStatus = StreamStatus.ACTIVE)
      )
      val shardToMerge =
        active.streams(streamName).shards.keys.head
      val adjacentShardToMerge =
        ShardId.create(
          active
            .streams(streamName)
            .shards
            .keys
            .toList
            .map(_.shardId.index)
            .max + 1
        )

      val req =
        MergeShardsRequest(
          adjacentShardToMerge.shardId,
          shardToMerge.shardId.shardId,
          streamName
        )

      for {
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        res <- req.mergeShards(active, shardSemaphores)
      } yield assert(
        res.isInvalid,
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the shard is not found")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, shardSemaphoreKeys) =
        Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)
      val active = streams.findAndUpdateStream(streamName)(s =>
        s.copy(streamStatus = StreamStatus.ACTIVE)
      )
      val shardToMerge =
        ShardId.create(
          active
            .streams(streamName)
            .shards
            .keys
            .toList
            .map(_.shardId.index)
            .max + 1
        )
      val adjacentShardToMerge =
        streams.streams(streamName).shards.keys.toList(1)

      val req =
        MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId,
          streamName
        )

      for {
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        res <- req.mergeShards(active, shardSemaphores)
      } yield assert(
        res.isInvalid,
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the shards are not adjacent")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, shardSemaphoreKeys) =
        Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)
      val active = streams.findAndUpdateStream(streamName)(s =>
        s.copy(streamStatus = StreamStatus.ACTIVE)
      )
      val shardToMerge =
        streams.streams(streamName).shards.keys.head
      val adjacentShardToMerge =
        streams.streams(streamName).shards.keys.toList(2)

      val req =
        MergeShardsRequest(
          adjacentShardToMerge.shardId.shardId,
          shardToMerge.shardId.shardId,
          streamName
        )

      for {
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        res <- req.mergeShards(active, shardSemaphores)
      } yield assert(
        res.isInvalid,
        s"req: $req\nres: $res"
      )
  })
}
