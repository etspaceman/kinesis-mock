package kinesis.mock
package api

import cats.effect._
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
        shardSemaphores <- shardSemaphoreKeys
          .traverse(k => Semaphore[IO](1).map(s => k -> s))
          .map(_.toMap)
        res <- req.putRecord(active, shardSemaphores)
      } yield assert(
        res.isValid && res.exists { case (resultStreams, _) =>
          resultStreams.streams.get(streamName).exists { stream =>
            stream.shards.values.toList.flatten.exists { rec =>
              rec.data.sameElements(initReq.data)
            }
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
          shardSemaphores <- shardSemaphoreKeys
            .traverse(k => Semaphore[IO](1).map(s => k -> s))
            .map(_.toMap)
          res <- req.putRecord(streams, shardSemaphores)
        } yield assert(
          res.isInvalid,
          s"req: $req\nres: $res"
        )
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
          shardSemaphores <- shardSemaphoreKeys
            .traverse(k => Semaphore[IO](1).map(s => k -> s))
            .map(_.toMap)
          res <- req.putRecord(updated, shardSemaphores)
        } yield assert(
          res.isInvalid,
          s"req: $req\nres: $res"
        )
    }
  )
}
