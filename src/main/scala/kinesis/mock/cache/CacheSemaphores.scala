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

import cats.effect._
import cats.effect.std.Semaphore

final case class CacheSemaphores private (
    addTagsToStream: Semaphore[IO],
    removeTagsFromStream: Semaphore[IO],
    createStream: Semaphore[IO],
    deleteStream: Semaphore[IO],
    describeLimits: Semaphore[IO],
    describeStream: Semaphore[IO],
    registerStreamConsumer: Semaphore[IO],
    deregisterStreamConsumer: Semaphore[IO],
    describeStreamConsumer: Semaphore[IO],
    describeStreamSummary: Semaphore[IO],
    listShards: Semaphore[IO],
    listStreamConsumers: Semaphore[IO],
    listStreams: Semaphore[IO],
    listTagsForStream: Semaphore[IO],
    mergeShards: Semaphore[IO],
    splitShard: Semaphore[IO]
)

object CacheSemaphores {
  def create(implicit C: Concurrent[IO]): IO[CacheSemaphores] = for {
    addTagsToStream <- Semaphore[IO](5)
    removeTagsFromStream <- Semaphore[IO](5)
    createStream <- Semaphore[IO](5)
    deleteStream <- Semaphore[IO](5)
    describeLimits <- Semaphore[IO](1)
    describeStream <- Semaphore[IO](10)
    registerStreamConsumer <- Semaphore[IO](5)
    deregisterStreamConsumer <- Semaphore[IO](5)
    describeStreamConsumer <- Semaphore[IO](10)
    describeStreamSummary <- Semaphore[IO](20)
    listShards <- Semaphore[IO](100)
    listStreamConsumers <- Semaphore[IO](5)
    listStreams <- Semaphore[IO](5)
    listTagsForStream <- Semaphore[IO](5)
    mergeShards <- Semaphore[IO](5)
    splitShard <- Semaphore[IO](5)
  } yield new CacheSemaphores(
    addTagsToStream,
    removeTagsFromStream,
    createStream,
    deleteStream,
    describeLimits,
    describeStream,
    registerStreamConsumer,
    deregisterStreamConsumer,
    describeStreamConsumer,
    describeStreamSummary,
    listShards,
    listStreamConsumers,
    listStreams,
    listTagsForStream,
    mergeShards,
    splitShard
  )
}
