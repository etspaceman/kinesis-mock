package kinesis.mock.cache

import scala.concurrent.duration._

import cats.effect.IO
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class UpdateStreamModeTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should update the stream mode")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        streamModeDetails = StreamModeDetails(StreamMode.PROVISIONED)
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
        _ <- cache
          .updateStreamMode(
            UpdateStreamModeRequest(
              StreamArn(awsRegion, streamName, cacheConfig.awsAccountId),
              streamModeDetails
            ),
            context,
            false
          )
          .rethrow
        res1 <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- IO.sleep(cacheConfig.updateStreamModeDuration.plus(400.millis))
        res2 <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
      } yield assert(
        res1.streamDescriptionSummary.streamModeDetails == streamModeDetails &&
          res1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
          res2.streamDescriptionSummary.streamModeDetails == streamModeDetails &&
          res2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE
      )
  })
}
