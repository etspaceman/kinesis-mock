package kinesis.mock
package api

import cats.effect._
import cats.effect.concurrent.Semaphore
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class UpdateShardCountTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should increase the shard count")(PropF.forAllF {
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

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamName,
          10
        )

      for {
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        res <- req.updateShardCount(active, shardSemaphores, 50)
      } yield assert(
        res.isValid && res.exists { case (resultStreams, _) =>
          resultStreams.streams.get(streamName).exists { stream =>
            val shards = stream.shards.keys.toList
            shards.count(_.isOpen) == 10 &&
            shards.filterNot(_.isOpen).map(_.shardId) == active
              .streams(streamName)
              .shards
              .keys
              .toList
              .map(_.shardId) &&
            stream.streamStatus == StreamStatus.UPDATING
          }
        },
        s"req: $req\n" +
          s"resOpenShards: ${res.map { case (resultStreams, _) =>
            resultStreams.streams(streamName).shards.keys.toList.filter(_.isOpen).map(_.shardId)
          }}\n" +
          s"resClosedShards: ${res.map { case (resultStreams, _) =>
            resultStreams.streams(streamName).shards.keys.toList.filterNot(_.isOpen).map(_.shardId)
          }}\n" +
          s"inputShards: ${active.streams(streamName).shards.keys.toList.map(_.shardId)}"
      )
  })

  test("It should decrease the shard count")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, shardSemaphoreKeys) =
        Streams.empty.addStream(10, streamName, awsRegion, awsAccountId)
      val active =
        streams.findAndUpdateStream(streamName)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamName,
          5
        )

      for {
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        res <- req.updateShardCount(active, shardSemaphores, 50)
      } yield assert(
        res.isValid && res.exists { case (resultStreams, _) =>
          resultStreams.streams.get(streamName).exists { stream =>
            val shards = stream.shards.keys.toList
            shards.count(_.isOpen) == 5 &&
            shards.filterNot(_.isOpen).map(_.shardId) == active
              .streams(streamName)
              .shards
              .keys
              .toList
              .map(_.shardId) &&
            stream.streamStatus == StreamStatus.UPDATING
          }
        },
        s"req: $req\n" +
          s"resOpenShards: ${res.map { case (resultStreams, _) =>
            resultStreams.streams(streamName).shards.keys.toList.filter(_.isOpen).map(_.shardId)
          }}\n" +
          s"resClosedShards: ${res.map { case (resultStreams, _) =>
            resultStreams.streams(streamName).shards.keys.toList.filterNot(_.isOpen).map(_.shardId)
          }}\n" +
          s"inputShards: ${active.streams(streamName).shards.keys.toList.map(_.shardId)}"
      )
  })

  test("It should reject an increase > 2x the current shard count")(
    PropF.forAllF {
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

        val req =
          UpdateShardCountRequest(
            ScalingType.UNIFORM_SCALING,
            streamName,
            11
          )

        for {
          shardSemaphores <- shardSemaphoreKeys
            .traverse(k => Semaphore[IO](1).map(s => k -> s))
            .map(_.toMap)
          res <- req.updateShardCount(active, shardSemaphores, 50)
        } yield assert(
          res.isInvalid,
          s"req: $req\nres: $res"
        )
    }
  )

  test("It should reject a decrease < 50% the current shard count")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val (streams, shardSemaphoreKeys) =
          Streams.empty.addStream(10, streamName, awsRegion, awsAccountId)
        val active =
          streams.findAndUpdateStream(streamName)(s =>
            s.copy(streamStatus = StreamStatus.ACTIVE)
          )

        val req =
          UpdateShardCountRequest(
            ScalingType.UNIFORM_SCALING,
            streamName,
            4
          )

        for {
          shardSemaphores <- shardSemaphoreKeys
            .traverse(k => Semaphore[IO](1).map(s => k -> s))
            .map(_.toMap)
          res <- req.updateShardCount(active, shardSemaphores, 50)
        } yield assert(
          res.isInvalid,
          s"req: $req\nres: $res"
        )
    }
  )

  test("It should reject an increase > the shard limit")(PropF.forAllF {
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

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamName,
          10
        )

      for {
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        res <- req.updateShardCount(active, shardSemaphores, 9)
      } yield assert(
        res.isInvalid,
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the stream is not active")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, shardSemaphoreKeys) =
        Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamName,
          10
        )

      for {
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        res <- req.updateShardCount(streams, shardSemaphores, 50)
      } yield assert(
        res.isInvalid,
        s"req: $req\nres: $res"
      )
  })
}
