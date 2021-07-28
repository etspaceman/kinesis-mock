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

class DecreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should decrease the stream retention period")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          context = LoggingContext.create
          _ <- cache
            .createStream(CreateStreamRequest(1, streamName), context, false)
            .rethrow
          _ <- IO.sleep(cacheConfig.createStreamDuration.plus(200.millis))
          _ <- cache
            .increaseStreamRetention(
              IncreaseStreamRetentionPeriodRequest(48, streamName),
              context,
              false
            )
            .rethrow
          _ <- cache
            .decreaseStreamRetention(
              DecreaseStreamRetentionPeriodRequest(24, streamName),
              context,
              false
            )
            .rethrow
          res <- cache
            .describeStreamSummary(
              DescribeStreamSummaryRequest(streamName),
              context,
              false
            )
            .rethrow
        } yield assert(res.streamDescriptionSummary.retentionPeriodHours == 24)
      )
  })
}
