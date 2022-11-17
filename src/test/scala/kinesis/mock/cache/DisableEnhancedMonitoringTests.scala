package kinesis.mock.cache

import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class DisableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should add disable enhanced monitoring")(PropF.forAllF {
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
            CreateStreamRequest(Some(1), streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        _ <- cache
          .enableEnhancedMonitoring(
            EnableEnhancedMonitoringRequest(
              Vector(ShardLevelMetric.ALL),
              streamName
            ),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        res <- cache
          .disableEnhancedMonitoring(
            DisableEnhancedMonitoringRequest(
              Vector(ShardLevelMetric.IncomingBytes),
              streamName
            ),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
        streamMonitoring <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(streamName),
            context,
            false,
            Some(awsRegion)
          )
          .rethrow
          .map(
            _.streamDescriptionSummary.enhancedMonitoring
              .flatMap(_.shardLevelMetrics)
          )
      } yield assert(
        res.desiredShardLevelMetrics == streamMonitoring && !res.desiredShardLevelMetrics
          .contains(ShardLevelMetric.IncomingBytes)
      )
  })
}
