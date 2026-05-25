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

import scala.jdk.CollectionConverters.*

import java.util.stream.Collectors

import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.syntax.javaFuture.*

class DisableEnhancedMonitoringTests extends AwsFunctionalTests:

  fixture().test("It should disable enhanced monitoring") { resources =>
    for
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
    yield assert(
      res.desiredShardLevelMetrics == streamMonitoring && !res
        .desiredShardLevelMetrics()
        .asScala
        .contains(MetricsName.INCOMING_BYTES),
      s"$res\n$streamMonitoring"
    )
  }
