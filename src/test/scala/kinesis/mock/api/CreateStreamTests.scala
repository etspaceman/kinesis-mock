package kinesis.mock
package api

import cats.effect.IO
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import cats.effect.Ref

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
        res <- req.createStream(
          streamsRef,
          req.shardCount,
          awsRegion,
          awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(req.streamName).exists { stream =>
          stream.shards.size == req.shardCount
        },
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
          res <- req.createStream(
            streamsRef,
            req.shardCount - 1,
            awsRegion,
            awsAccountId
          )
        } yield assert(res.isLeft, s"req: $req\nres: $res")
    }
  )
}
