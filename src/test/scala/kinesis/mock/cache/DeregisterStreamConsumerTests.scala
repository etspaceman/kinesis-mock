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

class DeregisterStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should deregister a stream consumer")(PropF.forAllF {
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
        _ <- cache
          .registerStreamConsumer(
            RegisterStreamConsumerRequest(
              consumerArn.consumerName,
              consumerArn.streamArn
            ),
            context,
            false
          )
          .rethrow
        _ <- IO.sleep(
          cacheConfig.registerStreamConsumerDuration.plus(200.millis)
        )
        _ <- cache
          .deregisterStreamConsumer(
            DeregisterStreamConsumerRequest(
              None,
              Some(consumerArn.consumerName),
              Some(consumerArn.streamArn)
            ),
            context,
            false
          )
          .rethrow
        describeStreamConsumerReq = DescribeStreamConsumerRequest(
          None,
          Some(consumerArn.consumerName),
          Some(consumerArn.streamArn)
        )
        checkStream1 <- cache
          .describeStreamConsumer(
            describeStreamConsumerReq,
            context,
            false
          )
          .rethrow
        _ <- IO.sleep(
          cacheConfig.deregisterStreamConsumerDuration.plus(200.millis)
        )
        checkStream2 <- cache.describeStreamConsumer(
          describeStreamConsumerReq,
          context,
          false
        )
      } yield assert(
        checkStream1.consumerDescription.consumerStatus == ConsumerStatus.DELETING &&
          checkStream2.isLeft,
        s"$checkStream1\n$checkStream2"
      )
  })
}
