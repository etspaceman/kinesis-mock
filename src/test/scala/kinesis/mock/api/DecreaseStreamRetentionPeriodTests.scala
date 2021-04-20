package kinesis.mock.api

import scala.concurrent.duration._

import cats.effect.IO
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import cats.effect.Ref

class DecreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {
  test("It should decrease the stream retention period")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)
      val withIncreasedRetention =
        streams.findAndUpdateStream(streamName)(stream =>
          stream.copy(
            retentionPeriod = 48.hours,
            streamStatus = StreamStatus.ACTIVE
          )
        )
      for {
        streamsRef <- Ref.of[IO, Streams](withIncreasedRetention)
        req = DecreaseStreamRetentionPeriodRequest(24, streamName)
        res <- req.decreaseStreamRetention(streamsRef)
        s <- streamsRef.get
      } yield assert(
        res.isValid && s.streams.get(streamName).exists { stream =>
          stream.retentionPeriod == 24.hours
        },
        s"req: $req\nres: $res\nstreams: $withIncreasedRetention"
      )
  })

  test(
    "It should reject when the stream retention period is less than the request"
  )(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      for {
        streamsRef <- Ref.of[IO, Streams](streams)
        req = DecreaseStreamRetentionPeriodRequest(48, streamName)
        res <- req.decreaseStreamRetention(streamsRef)
        streams <- streamsRef.get
      } yield assert(
        res.isInvalid && streams.streams.get(streamName).exists { stream =>
          stream.retentionPeriod == 24.hours
        },
        s"req: $req\nres: $res\nstreams: $streams"
      )
  })
}
