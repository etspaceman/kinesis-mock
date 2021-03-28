package kinesis.mock
package api

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamSummaryTests extends munit.ScalaCheckSuite {
  property("It should describe a stream summary")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val (streams, _) =
        Streams.empty.addStream(1, streamName, awsRegion, awsAccountId)

      val req =
        DescribeStreamSummaryRequest(streamName)
      val res = req.describeStreamSummary(streams)
      val streamDescriptionSummary = streams.streams
        .get(streamName)
        .map(s => StreamDescriptionSummary.fromStreamData(s))

      (res.isValid && res.exists { response =>
        streamDescriptionSummary.contains(response.streamDescriptionSummary)
      }) :| s"req: $req\nres: $res"
  })

  property("It should reject if the stream does not exist")(forAll {
    req: DescribeStreamSummaryRequest =>
      val streams = Streams.empty

      val res = req.describeStreamSummary(streams)

      res.isInvalid :| s"req: $req\nres: $res"
  })
}
