package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.{Blocker, IO}
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class RegisterStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should register a stream consumer")(PropF.forAllF {
    (
        streamName: StreamName,
        consumerName: ConsumerName
    ) =>
      Blocker[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          context = LoggingContext.create
          _ <- cache
            .createStream(CreateStreamRequest(1, streamName), context)
            .rethrow
          _ <- IO.sleep(cacheConfig.createStreamDuration.plus(200.millis))
          streamArn <- cache
            .describeStreamSummary(
              DescribeStreamSummaryRequest(streamName),
              context
            )
            .rethrow
            .map(_.streamDescriptionSummary.streamArn)
          _ <- cache
            .registerStreamConsumer(
              RegisterStreamConsumerRequest(consumerName, streamArn),
              context
            )
            .rethrow
          describeStreamConsumerReq = DescribeStreamConsumerRequest(
            None,
            Some(consumerName),
            Some(streamArn)
          )
          checkStream1 <- cache
            .describeStreamConsumer(
              describeStreamConsumerReq,
              context
            )
            .rethrow
          _ <- IO.sleep(
            cacheConfig.registerStreamConsumerDuration.plus(200.millis)
          )
          checkStream2 <- cache
            .describeStreamConsumer(
              describeStreamConsumerReq,
              context
            )
            .rethrow
        } yield assert(
          checkStream1.consumerDescription.consumerStatus == ConsumerStatus.CREATING &&
            checkStream2.consumerDescription.consumerStatus == ConsumerStatus.ACTIVE,
          s"$checkStream1\n$checkStream2"
        )
      )
  })
}
