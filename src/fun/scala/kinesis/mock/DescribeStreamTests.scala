package kinesis.mock

import java.util.Collections

import cats.implicits._
import software.amazon.awssdk.services.kinesis.model._

class DescribeStreamTests
    extends munit.CatsEffectSuite
    with AwsFunctionalTests {

  fixture.test("It should describe a stream") { resources =>
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
        .openShardCount(1)
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
      res <- initializedStreams.map { case (name,_) =>
        describeStreamSummary(resources.kinesisClient, name).map(_.streamDescriptionSummary())
      }.parSequence
    } yield assert(
      res.map(x => x.streamName() -> x.openShardCount()) == initializedStreams &&
        res.forall(x => x.streamStatus() == StreamStatus.ACTIVE),
      s"$res"
    )
  }
}
