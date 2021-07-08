package kinesis.mock

import scala.jdk.CollectionConverters._

import cats.effect.IO
import cats.syntax.all._
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class ListStreamConsumersTests
    extends munit.CatsEffectSuite
    with AwsFunctionalTests {

  fixture.test("It should list stream consumers") { resources =>
    for {
      consumerNames <- IO(consumerNameGen.take(3).toVector.map(_.consumerName))
      streamSummary <- describeStreamSummary(resources)
      streamArn = streamSummary.streamDescriptionSummary().streamARN()
      registerRes <- consumerNames.traverse(consumerName =>
        resources.kinesisClient
          .registerStreamConsumer(
            RegisterStreamConsumerRequest
              .builder()
              .streamARN(streamArn)
              .consumerName(consumerName)
              .build()
          )
          .toIO
      )
      res <- resources.kinesisClient
        .listStreamConsumers(
          ListStreamConsumersRequest.builder().streamARN(streamArn).build()
        )
        .toIO
    } yield assert(
      res
        .consumers()
        .asScala
        .toVector
        .sortBy(_.consumerName())
        .map(x =>
          models.ConsumerSummary(
            x.consumerARN(),
            x.consumerCreationTimestamp(),
            models.ConsumerName(x.consumerName()),
            models.ConsumerStatus.withName(x.consumerStatusAsString())
          )
        ) === registerRes
        .map(_.consumer())
        .sortBy(_.consumerName())
        .map(x =>
          models.ConsumerSummary(
            x.consumerARN(),
            x.consumerCreationTimestamp(),
            models.ConsumerName(x.consumerName()),
            models.ConsumerStatus.withName(x.consumerStatusAsString())
          )
        ),
      s"$res\n$registerRes"
    )
  }
}
