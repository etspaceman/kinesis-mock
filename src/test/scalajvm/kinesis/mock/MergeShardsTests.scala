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

import scala.concurrent.duration.*
import scala.jdk.CollectionConverters.*

import cats.effect.IO
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.syntax.javaFuture.*

class MergeShardsTests extends AwsFunctionalTests:

  fixture().test("It should merge shards") { resources =>
    for
      _ <- resources.kinesisClient
        .updateShardCount(
          UpdateShardCountRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .scalingType(ScalingType.UNIFORM_SCALING)
            .targetShardCount(2)
            .build()
        )
        .toIO
      _ <- IO.sleep(
        resources.cacheConfig.updateShardCountDuration.plus(400.millis)
      )
      openShards <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .map(
          _.shards().asScala.toVector.filter(
            _.sequenceNumberRange()
              .endingSequenceNumber() == null // scalafix:ok
          )
        )
      withRanges = openShards.map(x =>
        (
          x,
          models.HashKeyRange(
            BigInt(x.hashKeyRange().endingHashKey()),
            BigInt(x.hashKeyRange().startingHashKey())
          )
        )
      )
      (shardToMerge, adjacentShardToMerge) = withRanges
        .map { case (shard, range) =>
          (shard, withRanges.filter(_._2.isAdjacent(range)).map(_._1))
        }
        .filter(_._2.nonEmpty)
        .head
      _ <- resources.kinesisClient
        .mergeShards(
          MergeShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .adjacentShardToMerge(adjacentShardToMerge.head.shardId())
            .shardToMerge(shardToMerge.shardId())
            .build()
        )
        .toIO
      _ <- IO.sleep(resources.cacheConfig.mergeShardsDuration.plus(400.millis))
      openShards2 <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .map(
          _.shards().asScala.toVector.filter(
            _.sequenceNumberRange()
              .endingSequenceNumber() == null // scalafix:ok
          )
        )
    yield assert(
      openShards2.length == 1,
      s"$openShards\n$openShards2"
    )
  }
