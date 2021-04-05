package kinesis.mock

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class DescribeStreamConsumerTests
    extends munit.CatsEffectSuite
    with AwsFunctionalTests {

  fixture.test("It should describe a stream consumer") { resources =>
    for {
      consumerName <- IO(consumerNameGen.one.consumerName)
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      registerRes <- resources.kinesisClient
        .registerStreamConsumer(
          RegisterStreamConsumerRequest
            .builder()
            .streamARN(streamArn)
            .consumerName(consumerName)
            .build()
        )
        .toIO
      res <- describeStreamConsumer(resources, consumerName, streamArn)
    } yield assert(
      res
        .consumerDescription()
        .consumerARN == registerRes.consumer().consumerARN() &&
        res
          .consumerDescription()
          .consumerName == registerRes.consumer().consumerName() &&
        res.consumerDescription().consumerCreationTimestamp == registerRes
          .consumer()
          .consumerCreationTimestamp() &&
        res
          .consumerDescription()
          .consumerStatus == registerRes.consumer().consumerStatus(),
      s"$res\n$registerRes"
    )
  }
}
