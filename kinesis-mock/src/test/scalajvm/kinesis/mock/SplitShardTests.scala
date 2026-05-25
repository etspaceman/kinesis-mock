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

class SplitShardTests extends AwsFunctionalTests:

  fixture().test("It should split a shard") { resources =>
    for
      shard <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .map(_.shards().asScala.head)
      _ <- resources.kinesisClient
        .splitShard(
          SplitShardRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .newStartingHashKey(
              (BigInt(shard.hashKeyRange().endingHashKey()) / BigInt(2))
                .toString()
            )
            .shardToSplit(shard.shardId())
            .build()
        )
        .toIO
      _ <- IO.sleep(resources.cacheConfig.splitShardDuration.plus(400.millis))
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
    yield assert(
      openShards.length == 4,
      s"$openShards"
    )
  }
