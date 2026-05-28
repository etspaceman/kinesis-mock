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

import java.time.Instant

import kinesis.mock.models.*

class SubscriptionRegistryTests extends munit.CatsEffectSuite:
  private val testArn = ConsumerArn(
    StreamArn(
      AwsRegion.US_EAST_1,
      StreamName("s"),
      AwsAccountId("000000000000")
    ),
    ConsumerName("c"),
    Instant.ofEpochSecond(1_700_000_000L)
  )
  private val testShardId = "shardId-000000000000"

  test("acquire then release allows re-acquire") {
    for
      reg <- SubscriptionRegistry.create
      a1 <- reg.tryAcquire(testArn, testShardId)
      _ <- reg.release(testArn, testShardId)
      a2 <- reg.tryAcquire(testArn, testShardId)
    yield
      assert(a1, "first acquire should succeed")
      assert(a2, "re-acquire after release should succeed")
  }

  test("duplicate acquire returns false") {
    for
      reg <- SubscriptionRegistry.create
      a1 <- reg.tryAcquire(testArn, testShardId)
      a2 <- reg.tryAcquire(testArn, testShardId)
    yield
      assert(a1)
      assert(!a2)
  }

  test("lease resource releases on exit") {
    for
      reg <- SubscriptionRegistry.create
      _ <- reg.lease(testArn, testShardId).use {
        case Some(_) => cats.effect.IO.unit
        case None    =>
          cats.effect.IO.raiseError(
            new AssertionError("first lease should acquire")
          )
      }
      // After use{} exits, the slot is free again
      a <- reg.tryAcquire(testArn, testShardId)
    yield assert(a, "slot should be released after lease use{} exits")
  }

  test("lease returns None on conflict") {
    for
      reg <- SubscriptionRegistry.create
      _ <- reg.tryAcquire(testArn, testShardId)
      result <- reg.lease(testArn, testShardId).use(cats.effect.IO.pure)
    yield assertEquals(result, None)
  }
