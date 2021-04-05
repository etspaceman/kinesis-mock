package kinesis.mock

import scala.jdk.CollectionConverters._

import java.util.stream.Collectors

import software.amazon.awssdk.services.kinesis.model._

import kinesis.mock.syntax.javaFuture._

class DisableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with AwsFunctionalTests {

  fixture.test("It should disable enhanced monitoring") { resources =>
    for {
      _ <- resources.kinesisClient
        .enableEnhancedMonitoring(
          EnableEnhancedMonitoringRequest
            .builder()
            .shardLevelMetrics(MetricsName.ALL)
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
      res <- resources.kinesisClient
        .disableEnhancedMonitoring(
          DisableEnhancedMonitoringRequest
            .builder()
            .shardLevelMetrics(MetricsName.INCOMING_BYTES)
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
      streamMonitoring <- describeStreamSummary(resources).map(
        _.streamDescriptionSummary()
          .enhancedMonitoring()
          .stream()
          .flatMap(x => x.shardLevelMetrics().stream())
          .collect(Collectors.toList[MetricsName])
      )
    } yield assert(
      res.desiredShardLevelMetrics == streamMonitoring && !res
        .desiredShardLevelMetrics()
        .asScala
        .contains(MetricsName.INCOMING_BYTES),
      s"$res\n$streamMonitoring"
    )
  }
}
