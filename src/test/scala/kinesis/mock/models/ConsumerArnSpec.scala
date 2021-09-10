package kinesis.mock.models

import java.time.Instant

import enumeratum.scalacheck._
import org.scalacheck.Prop._

import kinesis.mock.instances.arbitrary._

class ConsumerArnSpec extends munit.ScalaCheckSuite {
  property("It should convert to a proper ARN format")(forAll {
    (
        streamArn: StreamArn,
        consumerName: ConsumerName
    ) =>
      val creationTime = Instant.now()
      val consumerArn = ConsumerArn(streamArn, consumerName, creationTime)

      val expected =
        s"arn:aws:kinesis:${streamArn.awsRegion.entryName}:${streamArn.awsAccountId}:stream/${streamArn.streamName}/consumer/$consumerName:${creationTime.getEpochSecond}"

      (consumerArn.consumerArn == expected) :| s"Calculated: ${consumerArn}\nExpected: ${expected}"
  })

  property("It should be constructed by an ARN string")(forAll {
    (
        streamArn: StreamArn,
        consumerName: ConsumerName
    ) =>
      val creationTime = Instant.now()
      val expected =
        s"arn:aws:kinesis:${streamArn.awsRegion.entryName}:${streamArn.awsAccountId}:stream/${streamArn.streamName}/consumer/$consumerName:${creationTime.getEpochSecond}"
      val consumerArn = ConsumerArn.fromArn(expected)

      (consumerArn.exists(
        _.consumerArn == expected
      )) :| s"Calculated: ${consumerArn}\nExpected: ${expected}\n"
  })
}
