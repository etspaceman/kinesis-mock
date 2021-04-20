package kinesis.mock.cache

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._
import cats.effect.Resource

class EnableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should add enable enhanced monitoring")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      Resource.unit[IO].use(blocker =>
        for {
          cacheConfig <- CacheConfig.read(blocker)
          cache <- Cache(cacheConfig)
          context = LoggingContext.create
          _ <- cache
            .createStream(CreateStreamRequest(1, streamName), context)
            .rethrow
          res <- cache
            .enableEnhancedMonitoring(
              EnableEnhancedMonitoringRequest(
                List(ShardLevelMetric.IncomingBytes),
                streamName
              ),
              context
            )
            .rethrow
          streamMonitoring <- cache
            .describeStreamSummary(
              DescribeStreamSummaryRequest(streamName),
              context
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
      )
  })
}
