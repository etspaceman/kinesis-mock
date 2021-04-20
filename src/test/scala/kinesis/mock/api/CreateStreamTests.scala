package kinesis.mock
package api

import cats.effect.IO
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import cats.effect.Ref
import cats.effect.std.Semaphore

class CreateStreamTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should create a stream")(PropF.forAllF {
    (
        req: CreateStreamRequest,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams = Streams.empty

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        shardSemaphoresRef <- Ref
          .of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](Map.empty)
        res <- req.createStream(
          streamsRef,
          shardSemaphoresRef,
          req.shardCount,
          awsRegion,
          awsAccountId
        )
        s <- streamsRef.get
        shardSemaphores <- shardSemaphoresRef.get
      } yield assert(
        res.isValid && s.streams.get(req.streamName).exists { stream =>
          stream.shards.size == req.shardCount
        } && shardSemaphores.size == req.shardCount,
        s"req: $req\nres: $res"
      )
  })

  test("It should reject if the shardCount exceeds the shardLimit")(
    PropF.forAllF {
      (
          req: CreateStreamRequest,
          awsRegion: AwsRegion,
          awsAccountId: AwsAccountId
      ) =>
        val streams = Streams.empty

        for {
          streamsRef <- Ref.of[IO, Streams](streams)
          shardSemaphoresRef <- Ref
            .of[IO, Map[ShardSemaphoresKey, Semaphore[IO]]](Map.empty)
          res <- req.createStream(
            streamsRef,
            shardSemaphoresRef,
            req.shardCount - 1,
            awsRegion,
            awsAccountId
          )
        } yield assert(res.isInvalid, s"req: $req\nres: $res")
    }
  )
}
