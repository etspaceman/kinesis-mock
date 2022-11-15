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
      val streams =
        Streams.empty.addStream(5, streamArn)
      val active =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamArn.streamName,
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
            r.currentShardCount == 10 &&
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
      val streams =
        Streams.empty.addStream(10, streamArn)
      val active =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamArn.streamName,
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
            r.currentShardCount == 5 &&
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

  test("It should decrease the shard count after recently updating the shard count")(PropF.forAllF {
    (
    streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(10, streamArn)
      val active =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamArn.streamName,
          6
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
        _ <- streamsRef.update(_.findAndUpdateStream(streamArn)(_.copy(streamStatus = StreamStatus.ACTIVE)))
        res2 <- req2.updateShardCount(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield {
        assert(
          res.isRight && res2.isRight && s.streams.get(streamArn).exists { stream =>
            val shards = stream.shards.keys.toVector
            shards.count(_.isOpen) == finalShardCount &&
//            shards.filterNot(_.isOpen).map(_.shardId) == active
//              .streams(streamArn)
//              .shards
//              .keys
//              .toVector
//              .map(_.shardId)// &&
            stream.streamStatus == StreamStatus.UPDATING &&
            res.exists { r =>
              r.currentShardCount == 6 &&
              r.targetShardCount == 6 &&
              r.streamName == streamArn.streamName
            } &&
            res2.exists { r =>
              r.currentShardCount == finalShardCount &&
              r.targetShardCount == finalShardCount &&
              r.streamName == streamArn.streamName
            }
          },
          s"req: $req\n" +
            s"res: $res\n" +
            s"resOpenShards: ${s.streams(streamArn).shards.keys.toVector.filter(_.isOpen).map(_.shardId)}\n" +
            s"resClosedShards: ${s.streams(streamArn).shards.keys.toVector.filterNot(_.isOpen).map(_.shardId)}\n" +
            s"inputShards: ${active.streams(streamArn).shards.keys.toVector.map(_.shardId)}"
        )
      }
  })

  test("It should increase the shard count after recently updating the shard count")(PropF.forAllF {
    (
    streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(10, streamArn)
      val active =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamArn.streamName,
          20
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
        _ <- streamsRef.update(_.findAndUpdateStream(streamArn)(_.copy(streamStatus = StreamStatus.ACTIVE)))
        res2 <- req2.updateShardCount(
          streamsRef,
          50,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield {
        assert(
          res.isRight && res2.isRight && s.streams.get(streamArn).exists { stream =>
            val shards = stream.shards.keys.toVector
            shards.count(_.isOpen) == finalShardCount &&
              //            shards.filterNot(_.isOpen).map(_.shardId) == active
              //              .streams(streamArn)
              //              .shards
              //              .keys
              //              .toVector
              //              .map(_.shardId)// &&
              stream.streamStatus == StreamStatus.UPDATING &&
              res.exists { r =>
                r.currentShardCount == 20 &&
                  r.targetShardCount == 20 &&
                  r.streamName == streamArn.streamName
              } &&
              res2.exists { r =>
                r.currentShardCount == finalShardCount &&
                  r.targetShardCount == finalShardCount &&
                  r.streamName == streamArn.streamName
              }
          },
          s"req: $req\n" +
            s"res: $res\n" +
            s"resOpenShards: ${s.streams(streamArn).shards.keys.toVector.filter(_.isOpen).map(_.shardId)}\n" +
            s"resClosedShards: ${s.streams(streamArn).shards.keys.toVector.filterNot(_.isOpen).map(_.shardId)}\n" +
            s"inputShards: ${active.streams(streamArn).shards.keys.toVector.map(_.shardId)}"
        )
      }
  })

  test("It should reject an increase > 2x the current shard count")(
    PropF.forAllF {
      (
        streamArn: StreamArn
      ) =>
        val streams =
          Streams.empty.addStream(5, streamArn)
        val active =
          streams.findAndUpdateStream(streamArn)(s =>
            s.copy(streamStatus = StreamStatus.ACTIVE)
          )

        val req =
          UpdateShardCountRequest(
            ScalingType.UNIFORM_SCALING,
            streamArn.streamName,
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
          Streams.empty.addStream(10, streamArn)
        val active =
          streams.findAndUpdateStream(streamArn)(s =>
            s.copy(streamStatus = StreamStatus.ACTIVE)
          )

        val req =
          UpdateShardCountRequest(
            ScalingType.UNIFORM_SCALING,
            streamArn.streamName,
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
        Streams.empty.addStream(5, streamArn)
      val active =
        streams.findAndUpdateStream(streamArn)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamArn.streamName,
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
        Streams.empty.addStream(5, streamArn)

      val req =
        UpdateShardCountRequest(
          ScalingType.UNIFORM_SCALING,
          streamArn.streamName,
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
