package kinesis.mock.api

import scala.concurrent.duration._

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DecreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should decrease the stream retention period")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn)
      val withIncreasedRetention =
        streams.findAndUpdateStream(streamArn)(stream =>
          stream.copy(
            retentionPeriod = 48.hours,
            streamStatus = StreamStatus.ACTIVE
          )
        )
      for {
        streamsRef <- Ref.of[IO, Streams](withIncreasedRetention)
        req = DecreaseStreamRetentionPeriodRequest(24, streamArn.streamName)
        res <- req.decreaseStreamRetention(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight && s.streams.get(streamArn).exists { stream =>
          stream.retentionPeriod == 24.hours
        },
        s"req: $req\nres: $res\nstreams: $withIncreasedRetention"
      )
  })

  test(
    "It should reject when the stream retention period is less than the request"
  )(PropF.forAllF {
    (
        streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DecreaseStreamRetentionPeriodRequest(48, streamArn.streamName)
        res <- req.decreaseStreamRetention(streamsRef, streamArn.awsRegion, streamArn.awsAccountId)
        streams <- streamsRef.get
      } yield assert(
        res.isLeft && streams.streams.get(streamArn).exists { stream =>
          stream.retentionPeriod == 24.hours
        },
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })
}
