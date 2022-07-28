package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import cats.syntax.all._
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DecreaseStreamRetentionPeriodTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should decrease the stream retention period")(PropF.forAllF {
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
            CreateStreamRequest(streamName, 1),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
        _ <- cache
          .increaseStreamRetention(
            IncreaseStreamRetentionPeriodRequest(48, streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- cache
          .decreaseStreamRetention(
            DecreaseStreamRetentionPeriodRequest(24, streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        res <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
      } yield assert(res.streamDescriptionSummary.retentionPeriodHours == 24)
  })
}
