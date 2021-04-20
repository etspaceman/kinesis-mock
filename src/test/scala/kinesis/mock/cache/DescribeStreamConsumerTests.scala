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
import cats.effect.Resource

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
      Resource.unit[IO].use(blocker =>
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
          registerRes <- cache
            .registerStreamConsumer(
              RegisterStreamConsumerRequest(consumerName, streamArn),
              context
            )
            .rethrow

          res <- cache
            .describeStreamConsumer(
              DescribeStreamConsumerRequest(
                None,
                Some(consumerName),
                Some(streamArn)
              ),
              context
            )
            .rethrow
        } yield assert(
          res.consumerDescription == registerRes.consumer,
          s"$registerRes\n$res"
        )
      )
  })
}
