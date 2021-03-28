package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import kinesis.mock.syntax.scalacheck._

class ListStreamConsumersTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should list stream consumers")(PropF.forAllF {
    (
      streamName: StreamName
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
        consumerNames <- IO(consumerNameArb.arbitrary.take(3))
        registerResults <- consumerNames.toList.traverse(consumerName =>
          cache
            .registerStreamConsumer(
              RegisterStreamConsumerRequest(consumerName, streamArn)
            )
            .rethrow
        )
        res <- consumerNames.toList.traverse(consumerName =>
          cache
            .describeStreamConsumer(
              DescribeStreamConsumerRequest(
                None,
                Some(consumerName),
                Some(streamArn)
              )
            )
            .rethrow
        )
      } yield assert(
        res.map(_.consumerDescription) == registerResults.map(_.consumer),
        s"$registerResults\n$res"
      )
  })
}
