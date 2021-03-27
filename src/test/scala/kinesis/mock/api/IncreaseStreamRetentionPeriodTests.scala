package kinesis.mock.api

import scala.concurrent.duration._

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class IncreaseStreamRetentionPeriodTests extends munit.ScalaCheckSuite {
  property("It should increase the stream retention period")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val active = streams.findAndUpdateStream(streamName)(
        _.copy(streamStatus = StreamStatus.ACTIVE)
      )

      val req = IncreaseStreamRetentionRequest(48, streamName)
      val res = req.increaseStreamRetention(active)

      (res.isValid && res.exists { s =>
        s.streams.get(streamName).exists { stream =>
          stream.retentionPeriod == 48.hours
        }
      }) :| s"req: $req\nres: $res\nstreams: $streams"
  })

  property(
    "It should reject when the stream retention period is greater than the request"
  )(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val withUpdatedRetention = streams.findAndUpdateStream(streamName)(s =>
        s.copy(retentionPeriod = 72.hours, streamStatus = StreamStatus.ACTIVE)
      )

      val req = IncreaseStreamRetentionRequest(48, streamName)
      val res = req.increaseStreamRetention(withUpdatedRetention)

      res.isInvalid :| s"req: $req\nres: $res\nstreams: $streams"
  })
}
