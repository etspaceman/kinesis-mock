package kinesis.mock.cache

import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DescribeStreamSummaryTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should describe a stream summary")(PropF.forAllF {
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
        res <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(Some(streamName), None),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        expected = StreamDescriptionSummary(
          Some(0),
          Some(EncryptionType.NONE),
          Vector(ShardLevelMetrics(Vector.empty)),
          None,
          1,
          24,
          StreamArn(awsRegion, streamName, cacheConfig.awsAccountId),
          res.streamDescriptionSummary.streamCreationTimestamp,
          StreamModeDetails(StreamMode.PROVISIONED),
          streamName,
          StreamStatus.CREATING
        )
      } yield assert(
        res.streamDescriptionSummary == expected,
        s"$res\n$expected"
      )
  })
}
