/*
 * Copyright 2021-2023 Typelevel
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

package kinesis.mock.cache

import cats.effect.IO
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary._
import kinesis.mock.models._

class EnableEnhancedMonitoringTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should add enable enhanced monitoring")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(cacheConfig => Cache(cacheConfig))
        .use { case cache =>
          for {
            context <- LoggingContext.create
            _ <- cache
              .createStream(
                CreateStreamRequest(Some(1), None, streamName),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
            res <- cache
              .enableEnhancedMonitoring(
                EnableEnhancedMonitoringRequest(
                  Vector(ShardLevelMetric.IncomingBytes),
                  Some(streamName),
                  None
                ),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
            streamMonitoring <- cache
              .describeStreamSummary(
                DescribeStreamSummaryRequest(Some(streamName), None),
                context,
                isCbor = false,
                Some(awsRegion)
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
        }
  })
}
