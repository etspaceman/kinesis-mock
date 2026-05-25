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

import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.syntax.javaFuture.*

class GetShardIteratorTests extends AwsFunctionalTests:

  fixture().test("It should get a shard iterator") { resources =>
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
      res <- resources.kinesisClient
        .getShardIterator(
          GetShardIteratorRequest
            .builder()
            .shardId(shard.shardId())
            .shardIteratorType(ShardIteratorType.TRIM_HORIZON)
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
        .attempt
    yield assert(
      res.isRight,
      s"$res"
    )
  }
