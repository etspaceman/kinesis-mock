package kinesis.mock.cache

import cats.effect.IO
import cats.syntax.all._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class EnableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should add enable enhanced monitoring")(PropF.forAllF {
    (
      streamName: StreamName
    ) =>
      for {
        cacheConfig <- CacheConfig.read.load[IO]
        cache <- Cache(cacheConfig)
        _ <- cache.createStream(CreateStreamRequest(1, streamName)).rethrow
        res <- cache
          .enableEnhancedMonitoring(
            EnableEnhancedMonitoringRequest(
              List(ShardLevelMetric.IncomingBytes),
              streamName
            )
          )
          .rethrow
        streamMonitoring <- cache
          .describeStreamSummary(DescribeStreamSummaryRequest(streamName))
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
