package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

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
        streamName: StreamName,
        consumerName: ConsumerName
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(50.millis))
        streamArn <- cache
          .describeStreamSummary(DescribeStreamSummaryRequest(streamName))
          .rethrow
          .map(_.streamDescriptionSummary.streamArn)
        _ <- cache
          .registerStreamConsumer(
            RegisterStreamConsumerRequest(consumerName, streamArn)
          )
          .rethrow
        _ <- IO.sleep(
          cacheConfig.registerStreamConsumerDuration.plus(50.millis)
        )
        _ <- cache
          .deregisterStreamConsumer(
            DeregisterStreamConsumerRequest(
              None,
              Some(consumerName),
              Some(streamArn)
            )
          )
          .rethrow
        describeStreamConsumerReq = DescribeStreamConsumerRequest(
          None,
          Some(consumerName),
          Some(streamArn)
        )
        checkStream1 <- cache
          .describeStreamConsumer(
            describeStreamConsumerReq
          )
          .rethrow
        _ <- IO.sleep(
          cacheConfig.deregisterStreamConsumerDuration.plus(50.millis)
        )
        checkStream2 <- cache.describeStreamConsumer(
          describeStreamConsumerReq
        )
      } yield assert(
        checkStream1.consumerDescription.consumerStatus == ConsumerStatus.DELETING &&
          checkStream2.isLeft,
        s"$checkStream1\n$checkStream2"
      )
  })
}
