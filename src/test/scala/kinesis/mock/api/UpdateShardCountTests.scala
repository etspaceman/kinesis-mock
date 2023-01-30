package kinesis.mock
package api

import cats.effect._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class UpdateShardCountTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should increase the shard count")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val initialShardCount = 5
      val streams =
        Streams.empty.addStream(initialShardCount, streamArn, None)
      val active =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          None,
          Some(streamArn),
          10
        )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.updateShardCount(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          val shards = stream.shards.keys.toVector
          shards.count(_.isOpen) == 10 &&
          shards.filterNot(_.isOpen).map(_.shardId) == active
            .streams(streamArn)
            .shards
            .keys
            .toVector
            .map(_.shardId) &&
          stream.streamStatus == StreamStatus.UPDATING &&
          res.exists { r =>
            r.currentShardCount == initialShardCount &&
            r.targetShardCount == 10 &&
            r.streamName == streamArn.streamName
          }
        },
        s"req: $req\n" +
          s"res: $res\n" +
          s"resOpenShards: ${s.streams(streamArn).shards.keys.toVector.filter(_.isOpen).map(_.shardId)}\n" +
          s"resClosedShards: ${s.streams(streamArn).shards.keys.toVector.filterNot(_.isOpen).map(_.shardId)}\n" +
          s"inputShards: ${active.streams(streamArn).shards.keys.toVector.map(_.shardId)}"
      )
  })

  test("It should decrease the shard count")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val initialShardCount = 10
      val streams =
        Streams.empty.addStream(initialShardCount, streamArn, None)
      val active =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          None,
          Some(streamArn),
          5
        )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.updateShardCount(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          val shards = stream.shards.keys.toVector
          shards.count(_.isOpen) == 5 &&
          shards.filterNot(_.isOpen).map(_.shardId) == active
            .streams(streamArn)
            .shards
            .keys
            .toVector
            .map(_.shardId) &&
          stream.streamStatus == StreamStatus.UPDATING &&
          res.exists { r =>
            r.currentShardCount == initialShardCount &&
            r.targetShardCount == 5 &&
            r.streamName == streamArn.streamName
          }
        },
        s"req: $req\n" +
          s"res: $res\n" +
          s"resOpenShards: ${s.streams(streamArn).shards.keys.toVector.filter(_.isOpen).map(_.shardId)}\n" +
          s"resClosedShards: ${s.streams(streamArn).shards.keys.toVector.filterNot(_.isOpen).map(_.shardId)}\n" +
          s"inputShards: ${active.streams(streamArn).shards.keys.toVector.map(_.shardId)}"
      )
  })

  test(
    "It should decrease the shard count after recently updating the shard count"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val initialShardCount = 10
      val streams =
        Streams.empty.addStream(initialShardCount, streamArn, None)
      val active: Streams =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val firstRoundShardCount = 6
      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          None,
          Some(streamArn),
          firstRoundShardCount
        )
      val finalShardCount = req.targetShardCount / 2
      val req2 = req.copy(targetShardCount = finalShardCount)

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.updateShardCount(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        _ <- streamsRef.update(
          _.findAndUpdateStream(streamArn)(
            _.copy(streamStatus = StreamStatus.ACTIVE)
          )
        )
        s <- streamsRef.get
        res2 <- req2.updateShardCount(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s2 <- streamsRef.get
      } yield {
        assert(
          res.isRight && s.streams.get(streamArn).exists { stream =>
            val shards = stream.shards.keys.toVector
            shards.count(_.isOpen) == firstRoundShardCount &&
            shards.filterNot(_.isOpen).map(_.shardId) == active
              .streams(streamArn)
              .shards
              .keys
              .toVector
              .map(_.shardId)
              .take(8) &&
            stream.streamStatus == StreamStatus.ACTIVE &&
            res.exists { r =>
              r.currentShardCount == initialShardCount &&
              r.targetShardCount == firstRoundShardCount &&
              r.streamName == streamArn.streamName
            }
          },
          s"req: $req\n" +
            s"res: $res\n" +
            s"resOpenShards: ${s.streams(streamArn).shards.keys.toVector.filter(_.isOpen).map(_.shardId)}\n" +
            s"resClosedShards: ${s.streams(streamArn).shards.keys.toVector.filterNot(_.isOpen).map(_.shardId)}\n" +
            s"inputShards: ${active.streams(streamArn).shards.keys.toVector.map(_.shardId)}"
        )
        assert(
          res2.isRight && s2.streams.get(streamArn).exists { stream =>
            val shards = stream.shards.keys.toVector
            shards.count(_.isOpen) == finalShardCount &&
            shards.filterNot(_.isOpen).map(_.shardId).sorted ==
              s.streams(streamArn).shards.keys.map(_.shardId).toVector.sorted
            stream.streamStatus == StreamStatus.UPDATING &&
            res2.exists { r =>
              r.currentShardCount == firstRoundShardCount &&
              r.targetShardCount == finalShardCount &&
              r.streamName == streamArn.streamName
            }
          },
          s"req2: $req2\n" +
            s"res2: $res2\n" +
            s"resOpenShards2: ${s2.streams(streamArn).shards.keys.toVector.filter(_.isOpen).map(_.shardId)}\n" +
            s"resClosedShards2: ${s2.streams(streamArn).shards.keys.toVector.filterNot(_.isOpen).map(_.shardId)}\n" +
            s"inputShards: ${s.streams(streamArn).shards.keys.toVector.map(_.shardId)}\n" +
            s"finalShardCount: ${finalShardCount}"
        )
      }
  })

  test(
    "It should increase the shard count after recently updating the shard count"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val initialShardCount = 10
      val streams =
        Streams.empty.addStream(initialShardCount, streamArn, None)
      val active =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val firstRoundShardCount = 20
      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          None,
          Some(streamArn),
          firstRoundShardCount
        )
      val finalShardCount = req.targetShardCount * 2
      val req2 = req.copy(targetShardCount = finalShardCount)

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.updateShardCount(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        _ <- streamsRef.update(
          _.findAndUpdateStream(streamArn)(
            _.copy(streamStatus = StreamStatus.ACTIVE)
          )
        )
        s <- streamsRef.get
        res2 <- req2.updateShardCount(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s2 <- streamsRef.get
      } yield {
        assert(
          res.isRight && s.streams.get(streamArn).exists { stream =>
            val shards = stream.shards.keys.toVector
            shards.count(_.isOpen) == firstRoundShardCount &&
            shards.filterNot(_.isOpen).map(_.shardId) == active
              .streams(streamArn)
              .shards
              .keys
              .toVector
              .map(_.shardId) &&
            stream.streamStatus == StreamStatus.ACTIVE &&
            res.exists { r =>
              r.currentShardCount == initialShardCount &&
              r.targetShardCount == firstRoundShardCount &&
              r.streamName == streamArn.streamName
            }
          },
          s"req: $req\n" +
            s"res: $res\n" +
            s"resOpenShards: ${s.streams(streamArn).shards.keys.toVector.filter(_.isOpen).map(_.shardId)}\n" +
            s"resClosedShards: ${s.streams(streamArn).shards.keys.toVector.filterNot(_.isOpen).map(_.shardId)}\n" +
            s"inputShards: ${active.streams(streamArn).shards.keys.toVector.map(_.shardId)}"
        )
        assert(
          res2.isRight && s2.streams.get(streamArn).exists { stream =>
            val shards = stream.shards.keys.toVector
            shards.count(_.isOpen) == finalShardCount &&
            shards.filterNot(_.isOpen).map(_.shardId).sorted ==
              s.streams(streamArn).shards.keys.map(_.shardId).toVector.sorted
            stream.streamStatus == StreamStatus.UPDATING &&
            res2.exists { r =>
              r.currentShardCount == firstRoundShardCount &&
              r.targetShardCount == finalShardCount &&
              r.streamName == streamArn.streamName
            }
          },
          s"req2: $req2\n" +
            s"res2: $res2\n" +
            s"resOpenShards2: ${s2.streams(streamArn).shards.keys.toVector.filter(_.isOpen).map(_.shardId)}\n" +
            s"resOpenShards2Size: ${s2.streams(streamArn).shards.keys.toVector.count(_.isOpen)}\n" +
            s"resClosedShards2: ${s2.streams(streamArn).shards.keys.toVector.filterNot(_.isOpen).map(_.shardId)}\n" +
            s"resClosedShards2Size: ${s2.streams(streamArn).shards.keys.toVector.count(x => !x.isOpen)}\n" +
            s"inputShards2: ${s.streams(streamArn).shards.keys.toVector.map(_.shardId)}\n" +
            s"finalShardCount: ${finalShardCount}"
        )
      }
  })

  test("It should reject an increase > 2x the current shard count")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        val streams =
          Streams.empty.addStream(5, streamArn, None)
        val active =
          streams.findAndUpdateStream(streamArn)(s =>
            s.copy(streamStatus = StreamStatus.ACTIVE)
          )

        val req =
          UpdateShardCountRequest(
            ScalingType.UNIFORM_SCALING,
            None,
            Some(streamArn),
            11
          )

        for {
          streamsRef <- Ref.of[IO, Streams](active)
          res <- req.updateShardCount(
            streamsRef,
            50,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )

  test("It should reject a decrease < 50% the current shard count")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        val streams =
          Streams.empty.addStream(10, streamArn, None)
        val active =
          streams.findAndUpdateStream(streamArn)(s =>
            s.copy(streamStatus = StreamStatus.ACTIVE)
          )

        val req =
          UpdateShardCountRequest(
            ScalingType.UNIFORM_SCALING,
            None,
            Some(streamArn),
            4
          )

        for {
          streamsRef <- Ref.of[IO, Streams](active)
          res <- req.updateShardCount(
            streamsRef,
            50,
            streamArn.awsRegion,
            streamArn.awsAccountId
          )
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )

  test("It should reject an increase > the shard limit")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(5, streamArn, None)
      val active =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          None,
          Some(streamArn),
          10
        )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        res <- req.updateShardCount(
          streamsRef,
          9,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })

  test("It should reject if the stream is not active")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(5, streamArn, None)

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          None,
          Some(streamArn),
          10
        )

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        res <- req.updateShardCount(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(res.isLeft, s"req: $req\nres: $res")
  })
}
