package kinesis.mock
package api

import cats.effect.{Ref, _}
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
      val streams =
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
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.updateShardCount(streamsRef, 50)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamName).exists { stream =>
          val shards = stream.shards.keys.toVector.sorted
          shards.count(_.isOpen) == 10 &&
          shards.filterNot(_.isOpen).map(_.shardId) == active
            .streams(streamName)
            .shards
            .keys
            .toVector
            .sorted
            .map(_.shardId) &&
          stream.streamStatus == StreamStatus.UPDATING
        },
        s"req: $req\n" +
          s"resOpenShards: ${s.streams(streamName).shards.keys.toVector.filter(_.isOpen).map(_.shardId)}\n" +
          s"resClosedShards: ${s.streams(streamName).shards.keys.toVector.filterNot(_.isOpen).map(_.shardId)}\n" +
          s"inputShards: ${active.streams(streamName).shards.keys.toVector.map(_.shardId)}"
      )
  })

  test("It should decrease the shard count")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
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
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.updateShardCount(streamsRef, 50)
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamName).exists { stream =>
          val shards = stream.shards.keys.toVector.sorted
          shards.count(_.isOpen) == 5 &&
          shards.filterNot(_.isOpen).map(_.shardId) == active
            .streams(streamName)
            .shards
            .keys
            .toVector
            .sorted
            .map(_.shardId) &&
          stream.streamStatus == StreamStatus.UPDATING
        },
        s"req: $req\n" +
          s"resOpenShards: ${s.streams(streamName).shards.keys.toVector.filter(_.isOpen).map(_.shardId)}\n" +
          s"resClosedShards: ${s.streams(streamName).shards.keys.toVector.filterNot(_.isOpen).map(_.shardId)}\n" +
          s"inputShards: ${active.streams(streamName).shards.keys.toVector.map(_.shardId)}"
      )
  })

  test("It should reject an increase > 2x the current shard count")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val streams =
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
          streamsRef <- Ref.of[IO, Streams](active)
          res <- req.updateShardCount(streamsRef, 50)
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )

  test("It should reject a decrease < 50% the current shard count")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val streams =
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
          streamsRef <- Ref.of[IO, Streams](active)
          res <- req.updateShardCount(streamsRef, 50)
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )

  test("It should reject an increase > the shard limit")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
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
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.updateShardCount(streamsRef, 9)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject if the stream is not active")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(5, streamName, awsRegion, awsAccountId)

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamName,
          10
        )

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.updateShardCount(streamsRef, 50)
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })
}
