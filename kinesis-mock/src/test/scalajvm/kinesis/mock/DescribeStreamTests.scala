/*
 * Copyright 2021-2026 io.github.etspaceman
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
