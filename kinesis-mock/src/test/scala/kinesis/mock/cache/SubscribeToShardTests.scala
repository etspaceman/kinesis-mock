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
package cache

import scala.concurrent.duration.*

import cats.effect.IO

import kinesis.mock.api.*
import kinesis.mock.models.*

class SubscribeToShardTests extends munit.CatsEffectSuite:
  override val munitIOTimeout = 30.seconds

  test(
    "subscribeToShard emits at least one heartbeat event for an active stream + consumer"
  ) {
    CacheConfig.read
      .resource[IO]
      .flatMap(cfg => Cache(cfg).map(c => (cfg, c)))
      .use { case (cfg, cache) =>
        val streamName = StreamName("subscribe-to-shard-heartbeat")
        val region = cfg.awsRegion
        val streamArn = StreamArn(region, streamName, cfg.awsAccountId)
        for
          ctx <- LoggingContext.create
          _ <- cache
            .createStream(
              CreateStreamRequest(Some(1), None, streamName),
              ctx,
              isCbor = false,
              Some(region)
            )
            .rethrow
          _ <- IO.sleep(cfg.createStreamDuration.plus(400.millis))
          regRes <- cache
            .registerStreamConsumer(
              RegisterStreamConsumerRequest(ConsumerName("hb-c"), streamArn),
              ctx,
              isCbor = false
            )
            .rethrow
          _ <- IO.sleep(cfg.registerStreamConsumerDuration.plus(400.millis))
          consumerArn = regRes.consumer.consumerArn
          req = SubscribeToShardRequest(
            consumerArn,
            "shardId-000000000000",
            StartingPosition(ShardIteratorType.LATEST, None, None)
          )
          stream <- cache.subscribeToShard(req, ctx, isCbor = false, Some(region))
          bytes <- stream.interruptAfter(3.seconds).compile.toVector
        yield assert(
          bytes.size >= 12,
          s"expected at least one event-stream frame, got ${bytes.size} bytes"
        )
      }
  }
