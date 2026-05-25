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

import cats.effect.IO
import cats.implicits.*
import software.amazon.awssdk.services.kinesis.model.*

import kinesis.mock.syntax.javaFuture.*

class ListShardsTests extends AwsFunctionalTests:

  fixture().test("It should list shards") { resources =>
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
      res <- resources.kinesisClient
        .listShards(
          ListShardsRequest
            .builder()
            .streamName(resources.streamName.streamName)
            .build()
        )
        .toIO
    yield assert(
      res.shards().size() == 4,
      s"$res"
    )
  }

  fixture().test("It should list shards for initialized streams") { resources =>
    for res <- initializedStreams.map { case (name, _) =>
        resources.defaultRegionKinesisClient
          .listShards(
            ListShardsRequest
              .builder()
              .streamName(name)
              .build()
          )
          .toIO
          .map(name -> _.shards())
      }.parSequence
    yield assert(
      res.map { case (name, shards) =>
        name -> shards.size()
      } == initializedStreams,
      s"$res"
    )
  }
