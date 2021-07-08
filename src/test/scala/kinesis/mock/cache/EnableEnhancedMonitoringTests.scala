package kinesis.mock
package cache

import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class EnableEnhancedMonitoringTests extends KinesisMockSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should add enable enhanced monitoring")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read
        cache <- Cache(cacheConfig)
        context = LoggingContext.create
        _ <- cache
          .createStream(CreateStreamRequest(1, streamName), context, false)
          .rethrow
        res <- cache
          .enableEnhancedMonitoring(
            EnableEnhancedMonitoringRequest(
              Vector(ShardLevelMetric.IncomingBytes),
              streamName
            ),
            context,
            false
          )
          .rethrow
        streamMonitoring <- cache
          .describeStreamSummary(
            DescribeStreamSummaryRequest(streamName),
            context,
            false
          )
          .rethrow
          .map(
            _.streamDescriptionSummary.enhancedMonitoring
              .flatMap(_.shardLevelMetrics)
          )
      } yield assert(
        res.desiredShardLevelMetrics == streamMonitoring && res.desiredShardLevelMetrics
          .contains(ShardLevelMetric.IncomingBytes)
      )
  })
}
