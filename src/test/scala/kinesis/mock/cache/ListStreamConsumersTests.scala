package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.{Blocker, IO}
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
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
      Blocker[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          context = LoggingContext.create
          _ <- cache
            .createStream(CreateStreamRequest(1, streamName), context)
            .rethrow
          _ <- IO.sleep(cacheConfig.createStreamDuration.plus(50.millis))
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
          res <- consumerNames.toList.traverse(consumerName =>
            cache
              .describeStreamConsumer(
                DescribeStreamConsumerRequest(
                  None,
                  Some(consumerName),
                  Some(streamArn)
                ),
                context
              )
              .rethrow
          )
        } yield assert(
          res.map(_.consumerDescription) == registerResults.map(_.consumer),
          s"$registerResults\n$res"
        )
      )
  })
}
