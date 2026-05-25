package kinesis.mock

import scala.jdk.CollectionConverters.*

import java.util.stream.Collectors

import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.syntax.javaFuture.*

class EnableEnhancedMonitoringTests extends AwsFunctionalTests:

  fixture().test("It should enable enhanced monitoring") { resources =>
    for
      res <- resources.kinesisClient
        .enableEnhancedMonitoring(
          EnableEnhancedMonitoringRequest
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
    yield assert(
      res.desiredShardLevelMetrics == streamMonitoring && res
        .desiredShardLevelMetrics()
        .asScala
        .contains(MetricsName.INCOMING_BYTES),
      s"$res\n$streamMonitoring"
    )
  }
