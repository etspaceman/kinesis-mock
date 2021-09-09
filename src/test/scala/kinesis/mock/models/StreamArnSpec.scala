package kinesis.mock.models

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._

class StreamArnSpec extends munit.ScalaCheckSuite {
  property("It should convert to a proper ARN format")(forAll {
    (
        streamName: StreamName,
        awsRegion: AwsRegion,
        awsAccountId: AwsAccountId
    ) =>
      val streamArn = StreamArn(awsRegion, streamName, awsAccountId)

      val expected =
        s"arn:aws:kinesis:${awsRegion.entryName}:$awsAccountId:stream/$streamName"

      (streamArn.streamArn == expected) :| s"Calculated: ${streamArn}\nExpected: ${expected}"
  })
}
