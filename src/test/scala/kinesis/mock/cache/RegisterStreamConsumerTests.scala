package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import enumeratum.scalacheck._
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
        consumerName: ConsumerName,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        streamArn = StreamArn(awsRegion, streamName, cacheConfig.awsAccountId)
        _ <- cache
          .createStream(
            CreateStreamRequest(Some(1), streamName),
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
          cacheConfig.registerStreamConsumerDuration.plus(400.millis)
        )
        checkStream2 <- cache
          .describeStreamConsumer(
            describeStreamConsumerReq,
            context,
            false
          )
          .rethrow
      } yield assert(
        checkStream1.consumerDescription.consumerStatus == ConsumerStatus.CREATING &&
          checkStream2.consumerDescription.consumerStatus == ConsumerStatus.ACTIVE,
        s"$checkStream1\n$checkStream2"
      )
  })
}
