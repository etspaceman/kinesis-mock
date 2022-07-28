package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import enumeratum.scalacheck._
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
        consumerName: ConsumerName,
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        streamArn = StreamArn(awsRegion, streamName, cacheConfig.awsAccountId)
        _ <- cache
          .createStream(
            CreateStreamRequest(1, streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
        _ <- cache
          .registerStreamConsumer(
            RegisterStreamConsumerRequest(consumerName, streamArn),
            context,
            false
          )
          .rethrow
        _ <- IO.sleep(
          cacheConfig.registerStreamConsumerDuration.plus(400.millis)
        )
        _ <- cache
          .deregisterStreamConsumer(
            DeregisterStreamConsumerRequest(
              None,
              Some(consumerName),
              Some(streamArn)
            ),
            context,
            false
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
            context,
            false
          )
          .rethrow
        _ <- IO.sleep(
          cacheConfig.deregisterStreamConsumerDuration.plus(400.millis)
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
