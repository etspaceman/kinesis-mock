package kinesis.mock.api

import scala.concurrent.duration._

import cats.effect.IO
import cats.effect.concurrent.Ref
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class IncreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should increase the stream retention period")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val active = streams.findAndUpdateStream(streamName)(
        _.copy(streamStatus = StreamStatus.ACTIVE)
      )

      for {
        streamsRef <- Ref.of[IO, Streams](active)
        req = IncreaseStreamRetentionPeriodRequest(48, streamName)
        res <- req.increaseStreamRetention(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isRight &&
          s.streams.get(streamName).exists { stream =>
            stream.retentionPeriod == 48.hours
          },
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })

  test(
    "It should reject when the stream retention period is greater than the request"
  )(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streams =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val withUpdatedRetention = streams.findAndUpdateStream(streamName)(s =>
        s.copy(retentionPeriod = 72.hours, streamStatus = StreamStatus.ACTIVE)
      )

      for {
        streamsRef <- Ref.of[IO, Streams](withUpdatedRetention)
        req = IncreaseStreamRetentionPeriodRequest(48, streamName)
        res <- req.increaseStreamRetention(streamsRef)
      } yield assert(res.isLeft, s"req: $req\nres: $res\nstreams: $streams")
  })
}
