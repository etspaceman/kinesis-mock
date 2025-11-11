package kinesis.mock

import scala.jdk.CollectionConverters._

import cats.effect.IO
import cats.syntax.all._
import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.instances.arbitrary._
import kinesis.mock.models.ConsumerArn
import kinesis.mock.syntax.javaFuture._
import kinesis.mock.syntax.scalacheck._

class ListStreamConsumersTests extends AwsFunctionalTests {

  fixture().test("It should list stream consumers") { resources =>
    for {
      consumerNames <- IO(
        consumerNameGen.take(3).toList.sorted.map(_.consumerName)
      )
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
      resultConsumers <- res.consumers.asScala.toList.traverse(x =>
        IO.fromEither(
          ConsumerArn.fromArn(x.consumerARN()).leftMap(new RuntimeException(_))
        ).map(consumerArn =>
          models.ConsumerSummary(
            consumerArn,
            x.consumerCreationTimestamp(),
            models.ConsumerName(x.consumerName()),
            models.ConsumerStatus.withName(x.consumerStatusAsString())
          )
        )
      )
      registerResultConsumers <- registerRes
        .map(_.consumer())
        .traverse(x =>
          IO.fromEither(
            ConsumerArn
              .fromArn(x.consumerARN())
              .leftMap(new RuntimeException(_))
          ).map(consumerArn =>
            models.ConsumerSummary(
              consumerArn,
              x.consumerCreationTimestamp(),
              models.ConsumerName(x.consumerName()),
              models.ConsumerStatus.withName(x.consumerStatusAsString())
            )
          )
        )
    } yield assert(
      resultConsumers === registerResultConsumers,
      s"$res\n$registerRes"
    )
  }
}
