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

import scala.concurrent.duration._

import cats.effect.IO
import enumeratum.scalacheck._
import org.scalacheck.Test
import org.scalacheck.effect.PropF

import kinesis.mock.LoggingContext
import kinesis.mock.api._
import kinesis.mock.instances.arbitrary.given
import kinesis.mock.models._

class MergeShardsTests
    extends munit.CatsEffectSuite
    with munit.ScalaCheckEffectSuite {

  override def scalaCheckTestParameters: Test.Parameters =
    Test.Parameters.default.withMinSuccessfulTests(5)

  test("It should merge shards")(PropF.forAllF {
    (
        streamName: StreamName,
        awsRegion: AwsRegion
    ) =>
      CacheConfig.read
        .resource[IO]
        .flatMap(cacheConfig => Cache(cacheConfig).map(x => (cacheConfig, x)))
        .use { case (cacheConfig, cache) =>
          val context = LoggingContext.create
          for {
            _ <- cache
              .createStream(
                CreateStreamRequest(Some(5), None, streamName),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
            _ <- IO.sleep(cacheConfig.createStreamDuration.plus(400.millis))
            adjacentParentShardId = ShardId.create(1).shardId
            parentShardId = ShardId.create(0).shardId
            _ <- cache
              .mergeShards(
                MergeShardsRequest(
                  adjacentParentShardId,
                  parentShardId,
                  Some(streamName),
                  None
                ),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
            describeStreamSummaryReq = DescribeStreamSummaryRequest(
              Some(streamName),
              None
            )
            checkStream1 <- cache
              .describeStreamSummary(
                describeStreamSummaryReq,
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
            _ <- IO.sleep(cacheConfig.mergeShardsDuration.plus(500.millis))
            checkStream2 <- cache
              .describeStreamSummary(
                describeStreamSummaryReq,
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
            checkShards <- cache
              .listShards(
                ListShardsRequest(
                  None,
                  None,
                  None,
                  None,
                  None,
                  Some(streamName),
                  None
                ),
                context,
                isCbor = false,
                Some(awsRegion)
              )
              .rethrow
          } yield assert(
            checkStream1.streamDescriptionSummary.streamStatus == StreamStatus.UPDATING &&
              checkStream2.streamDescriptionSummary.streamStatus == StreamStatus.ACTIVE &&
              checkShards.shards.count(!_.isOpen) == 2 &&
              checkShards.shards.exists(shard =>
                shard.adjacentParentShardId.contains(
                  adjacentParentShardId
                ) && shard.parentShardId.contains(parentShardId)
              ) && checkShards.shards.length == 6,
            s"${checkShards.shards.mkString("\n\t")}\n" +
              s"$checkStream1\n" +
              s"$checkStream2"
          )
        }
  })
}
