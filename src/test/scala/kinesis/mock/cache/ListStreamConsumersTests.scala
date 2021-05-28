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
import org.scalacheck.Gen

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
          consumerNames <- IO(
            Gen
              .listOfN(3, consumerNameArb.arbitrary)
              .suchThat(x =>
                x.groupBy(identity)
                  .collect { case (_, y) if y.length > 1 => x }
                  .isEmpty
              )
              .one
          )
          registerResults <- consumerNames.toList.traverse(consumerName =>
            cache
              .registerStreamConsumer(
                RegisterStreamConsumerRequest(consumerName, streamArn),
                context,
                false
              )
              .rethrow
          )
          res <- cache
            .listStreamConsumers(
              ListStreamConsumersRequest(None, None, streamArn, None),
              context,
              false
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
