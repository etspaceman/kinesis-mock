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
        streamName: StreamName,
        consumerName: ConsumerName
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(CreateStreamRequest(1, streamName), context, false)
          .rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(200.millis))
        streamArn <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(streamName),
            context,
            false
          )
          .rethrow
          .map(_.streamDescriptionSummary.streamArn)
        registerRes <- cache
          .registerStreamConsumer(
            RegisterStreamConsumerRequest(consumerName, streamArn),
            context,
            false
          )
          .rethrow

        res <- cache
          .describeStreamConsumer(
            DescribeStreamConsumerRequest(
              None,
              Some(consumerName),
              Some(streamArn)
            ),
            context,
            false
          )
          .rethrow
      } yield assert(
        ConsumerSummary.fromConsumer(
          res.consumerDescription
        ) === registerRes.consumer && res.consumerDescription.streamArn == streamArn,
        s"$registerRes\n$res"
      )
  })
}
