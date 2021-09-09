package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should describe a stream consumer")(PropF.forAllF {
    (
      consumerArn: ConsumerArn
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(
            CreateStreamRequest(1, consumerArn.streamArn.streamName),
            context,
            false,
            Some(consumerArn.streamArn.awsRegion)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(200.millis))
        registerRes <- cache
          .registerStreamConsumer(
            RegisterStreamConsumerRequest(
              consumerArn.consumerName,
              consumerArn.streamArn
            ),
            context,
            false
          )
          .rethrow

        res <- cache
          .describeStreamConsumer(
            DescribeStreamConsumerRequest(
              None,
              Some(consumerArn.consumerName),
              Some(consumerArn.streamArn)
            ),
            context,
            false
          )
          .rethrow
      } yield assert(
        ConsumerSummary.fromConsumer(
          res.consumerDescription
        ) === registerRes.consumer && res.consumerDescription.streamArn == consumerArn.streamArn,
        s"$registerRes\n$res"
      )
  })
}
