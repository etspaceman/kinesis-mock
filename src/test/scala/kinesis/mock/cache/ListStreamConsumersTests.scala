package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.effect.PropF
import org.scalacheck.{Gen, Test}

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
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(
            CreateStreamRequest(Some(1), None, streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
        streamArn <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(streamName),
            context,
            false,
            Some(awsRegion)
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
        registerResults <- consumerNames.sorted.toVector.traverse(
          consumerName =>
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
        res.consumers == registerResults
          .map(_.consumer),
        s"${registerResults.map(_.consumer)}\n" +
          s"${res.consumers}"
      )
  })
}
