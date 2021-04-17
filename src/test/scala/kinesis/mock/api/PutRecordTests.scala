package kinesis.mock
package api

import cats.effect._
import cats.effect.concurrent.{Ref, Semaphore}
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import cats.effect.std.Semaphore

class PutRecordTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should put a record")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId,
        initReq: PutRecordRequest
    ) =>
      val (streams, shardSemaphoreKeys) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)
      val active =
        streams.findAndUpdateStream(streamName)(s =>
          s.copy(streamStatus = StreamStatus.ACTIVE)
        )

      val req = initReq.copy(
        streamName = streamName
      )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        shardSemaphoresRef <- Ref
          .of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](
            shardSemaphores
          )
        res <- req.putRecord(streamsRef, shardSemaphoresRef)
        s <- streamsRef.get
      } yield assert(
        res.isValid && s.streams.get(streamName).exists { stream =>
          stream.shards.values.toList.flatten.exists { rec =>
            rec.data.sameElements(initReq.data)
          }
        },
        s"req: $req\nres: $res"
      )
  })

  test("It should reject when the stream is not active or updating")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId,
          initReq: PutRecordRequest
      ) =>
        val (streams, shardSemaphoreKeys) =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val req = initReq.copy(
          streamName = streamName
        )

        for {
          streamsRef <- Ref.of[IO, Streams](streams)
          shardSemaphores <- shardSemaphoreKeys
            .traverse(k => Semaphore[IO](1).map(s => k -> s))
            .map(_.toMap)
          shardSemaphoresRef <- Ref
            .of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](
              shardSemaphores
            )
          res <- req.putRecord(streamsRef, shardSemaphoresRef)
        } yield assert(res.isInvalid, s"req: $req\nres: $res")
    }
  )

  test("It should reject when the shard is closed")(
    PropF.forAllF {
      (
          streamName: StreamName,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId,
          initReq: PutRecordRequest
      ) =>
        val (streams, shardSemaphoreKeys) =
          Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

        val updated = streams.findAndUpdateStream(streamName)(s =>
          s.copy(shards = s.shards.map { case (shard, recs) =>
            shard.copy(sequenceNumberRange =
              shard.sequenceNumberRange.copy(endingSequenceNumber =
                Some(SequenceNumber.shardEnd)
              )
            ) -> recs
          })
        )

        val req = initReq.copy(
          streamName = streamName
        )

        for {
          streamsRef <- Ref.of[IO, Streams](updated)
          shardSemaphores <- shardSemaphoreKeys
            .traverse(k => Semaphore[IO](1).map(s => k -> s))
            .map(_.toMap)
          shardSemaphoresRef <- Ref
            .of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](
              shardSemaphores
            )
          res <- req.putRecord(streamsRef, shardSemaphoresRef)
        } yield assert(res.isInvalid, s"req: $req\nres: $res")
    }
  )
}
