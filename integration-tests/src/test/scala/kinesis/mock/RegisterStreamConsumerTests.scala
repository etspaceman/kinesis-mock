package kinesis.mock

import scala.concurrent.duration._

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class RegisterStreamConsumerTests extends AwsFunctionalTests {

  fixture().test("It should register a stream consumer") { resources =>
    for {
      consumerName <- IO(consumerNameGen.one.consumerName)
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      _ <- resources.kinesisClient
        .registerStreamConsumer(
          RegisterStreamConsumerRequest
            .builder()
            .streamARN(streamArn)
            .consumerName(consumerName)
            .build()
        )
        .toIO
      check1 <- describeStreamConsumer(resources, consumerName, streamArn)
      _ <- IO.sleep(
        resources.cacheConfig.registerStreamConsumerDuration.plus(400.millis)
      )
      check2 <- describeStreamConsumer(
        resources,
        consumerName,
        streamArn
      )
    } yield assert(
      check1
        .consumerDescription()
        .consumerStatus() == ConsumerStatus.CREATING &&
        check2
          .consumerDescription()
          .consumerStatus() == ConsumerStatus.ACTIVE,
      s"$check1\n$check2"
    )
  }
}
