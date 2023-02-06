package kinesis.mock.cache

import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class EnableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should add enable enhanced monitoring")(PropF.forAllF {
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
          .enableEnhancedMonitoring(
            EnableEnhancedMonitoringRequest(
              Vector(ShardLevelMetric.IncomingBytes),
              Some(streamName),
              None
            ),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        streamMonitoring <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(Some(streamName), None),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
          .map(
            _.streamDescriptionSummary.enhancedMonitoring
              .map(_.flatMap(_.shardLevelMetrics))
          )
      } yield assert(
        res.desiredShardLevelMetrics == streamMonitoring && res.desiredShardLevelMetrics
          .exists(_.contains(ShardLevelMetric.IncomingBytes))
      )
  })
}
