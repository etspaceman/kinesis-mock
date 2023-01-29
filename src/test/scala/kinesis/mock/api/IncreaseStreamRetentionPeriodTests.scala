package kinesis.mock.api

import scala.concurrent.duration._

import cats.effect.{IO, Ref}
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class IncreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should increase the stream retention period")(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val active = streams.findAndUpdateStream(streamArn)(
        _.copy(streamStatus = StreamStatus.ACTIVE)
      )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        req = IncreaseStreamRetentionPeriodRequest(48, None, Some(streamArn))
        res <- req.increaseStreamRetention(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
        s <- streamsRef.get
      } yield assert(
        res.isRight &&
          s.streams.get(streamArn).exists { stream =>
            stream.retentionPeriod == 48.hours
          },
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test(
    "It should reject when the stream retention period is greater than the request"
  )(PropF.forAllF {
    (
      streamArn: StreamArn
    ) =>
      val streams =
        Streams.empty.addStream(1, streamArn, None)

      val withUpdatedRetention = streams.findAndUpdateStream(streamArn)(s =>
        s.copy(retentionPeriod = 72.hours, streamStatus = StreamStatus.ACTIVE)
      )

      for {
        streamsRef <- Ref.of[IO, Streams](withUpdatedRetention)
        req = IncreaseStreamRetentionPeriodRequest(48, None, Some(streamArn))
        res <- req.increaseStreamRetention(
          streamsRef,
          streamArn.awsRegion,
          streamArn.awsAccountId
        )
      } yield assert(res.isLeft, s"req: $req\nres: $res\nstreams: $streams")
  })
}
