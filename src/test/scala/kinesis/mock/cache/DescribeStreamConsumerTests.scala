package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamConsumerTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should describe a stream consumer")(PropF.forAllF {
    (
        streamName: StreamName,
        consumerName: ConsumerName
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(10.millis))
        streamArn <- cache
          .describeStreamSummary(DescribeStreamSummaryRequest(streamName))
          .rethrow
          .map(_.streamDescriptionSummary.streamArn)
        registerRes <- cache
          .registerStreamConsumer(
            RegisterStreamConsumerRequest(consumerName, streamArn)
          )
          .rethrow

        res <- cache
          .describeStreamConsumer(
            DescribeStreamConsumerRequest(
              None,
              Some(consumerName),
              Some(streamArn)
            )
          )
          .rethrow
      } yield assert(
        res.consumerDescription == registerRes.consumer,
        s"$registerRes\n$res"
      )
  })
}
