package kinesis.mock.api

import scala.concurrent.duration._

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DecreaseStreamRetentionPeriodTests extends munit.ScalaCheckSuite {
  property("It should decrease the stream retention period")(forAll {
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
      val req = DecreaseStreamRetentionPeriodRequest(24, streamName)
      val res = req.decreaseStreamRetention(withIncreasedRetention)

      (res.isValid && res.exists { s =>
        s.streams.get(streamName).exists { stream =>
          stream.retentionPeriod == 24.hours
        }
      }) :| s"req: $req\nres: $res\nstreams: $withIncreasedRetention"
  })

  property(
    "It should reject when the stream retention period is less than the request"
  )(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val req = DecreaseStreamRetentionPeriodRequest(48, streamName)
      val res = req.decreaseStreamRetention(streams)

      res.isInvalid :| s"req: $req\nres: $res\nstreams: $streams"
  })
}
