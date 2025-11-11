package kinesis.mock

import java.util.Collections

import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.syntax.javaFuture.*

class DescribeStreamTests extends AwsFunctionalTests:

  fixture().test("It should describe a stream") { resources =>
    for
      res <- resources.kinesisClient
        .describeStream(
          DescribeStreamRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build
        )
        .toIO
      expected = StreamDescription
        .builder()
        .encryptionType(EncryptionType.NONE)
        .enhancedMonitoring(
          EnhancedMetrics
            .builder()
            .shardLevelMetricsWithStrings(Collections.emptyList[String]())
            .build()
        )
        .hasMoreShards(false)
        .shards(res.streamDescription().shards())
        .retentionPeriodHours(24)
        .streamARN(
          s"arn:${resources.awsRegion.awsArnPiece}:kinesis:${resources.awsRegion.entryName}:${resources.cacheConfig.awsAccountId}:stream/${resources.streamName}"
        )
        .streamCreationTimestamp(
          res.streamDescription().streamCreationTimestamp()
        )
        .streamName(resources.streamName.streamName)
        .streamStatus(StreamStatus.ACTIVE)
        .streamModeDetails(
          StreamModeDetails.builder().streamMode(StreamMode.PROVISIONED).build()
        )
        .build()
    yield assert(
      res.streamDescription == expected,
      s"${res.streamDescription()}\n$expected"
    )
  }
