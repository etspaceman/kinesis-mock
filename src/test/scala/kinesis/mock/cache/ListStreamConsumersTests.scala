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
import kinesis.mock.syntax.scalacheck._
import cats.effect.Resource

class ListStreamConsumersTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should list stream consumers")(PropF.forAllF {
    (
      streamName: StreamName
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
          consumerNames <- IO(consumerNameArb.arbitrary.take(3))
          registerResults <- consumerNames.toList.traverse(consumerName =>
            cache
              .registerStreamConsumer(
                RegisterStreamConsumerRequest(consumerName, streamArn),
                context
              )
              .rethrow
          )
          res <- cache
            .listStreamConsumers(
              ListStreamConsumersRequest(None, None, streamArn, None),
              context
            )
            .rethrow
        } yield assert(
          res.consumers.sortBy(_.consumerName) == registerResults
            .map(_.consumer)
            .sortBy(_.consumerName),
          s"${registerResults.map(_.consumer).sortBy(_.consumerName)}\n" +
            s"${res.consumers.sortBy(_.consumerName)}"
        )
      )
  })
}
