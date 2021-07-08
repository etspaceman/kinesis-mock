package kinesis.mock

import java.util.Collections

import cats.syntax.all._
import software.amazon.awssdk.services.kinesis.model._

class DescribeStreamSummaryTests
    extends KinesisMockSuite
    with AwsFunctionalTests {

  fixture.test("It should describe a stream summary") { resources =>
    for {
      res <- describeStreamSummary(resources)
      expected = StreamDescriptionSummary
        .builder()
        .consumerCount(0)
        .encryptionType(EncryptionType.NONE)
        .enhancedMonitoring(
          EnhancedMetrics
            .builder()
            .shardLevelMetricsWithStrings(Collections.emptyList[String]())
            .build()
        )
        .openShardCount(genStreamShardCount)
        .retentionPeriodHours(24)
        .streamARN(
          s"arn:aws:kinesis:${resources.cacheConfig.awsRegion.entryName}:${resources.cacheConfig.awsAccountId}:stream/${resources.streamName}"
        )
        .streamCreationTimestamp(
          res.streamDescriptionSummary().streamCreationTimestamp()
        )
        .streamName(resources.streamName.streamName)
        .streamStatus(StreamStatus.ACTIVE)
        .build()
    } yield assert(
      res.streamDescriptionSummary == expected,
      s"${res.streamDescriptionSummary()}\n$expected"
    )
  }

  fixture.test("It should describe initialized streams") { resources =>
    for {
      res <- initializedStreams.parTraverse { case (name, _) =>
        describeStreamSummary(resources.kinesisClient, name).map(
          _.streamDescriptionSummary()
        )
      }
    } yield assert(
      res.map(s =>
        s.streamName() -> s.openShardCount()
      ) == initializedStreams &&
        res.forall(s => s.streamStatus() == StreamStatus.ACTIVE),
      s"$res"
    )
  }
}
