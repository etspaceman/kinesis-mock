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

package kinesis.mock.cache

import cats.effect.{IO, Ref, Resource}

import kinesis.mock.models.ConsumerArn

/** Tracks active SubscribeToShard subscriptions. AWS allows at most one active
  * subscription per (consumer, shard) pair — a duplicate request must be
  * rejected with ResourceInUseException until the previous one releases.
  */
final class SubscriptionRegistry private (
    ref: Ref[IO, Set[(ConsumerArn, String)]]
):
  def tryAcquire(consumerArn: ConsumerArn, shardId: String): IO[Boolean] =
    ref.modify { active =>
      val key = (consumerArn, shardId)
      if active.contains(key) then (active, false)
      else (active + key, true)
    }

  def release(consumerArn: ConsumerArn, shardId: String): IO[Unit] =
    ref.update(_ - ((consumerArn, shardId)))

  /** A bracketed lease. Yields Some(()) if acquired (will release on close),
    * None if another subscription is already active.
    */
  def lease(
      consumerArn: ConsumerArn,
      shardId: String
  ): Resource[IO, Option[Unit]] =
    Resource
      .make(tryAcquire(consumerArn, shardId)) {
        case true  => release(consumerArn, shardId)
        case false => IO.unit
      }
      .map(acquired => if acquired then Some(()) else None)

object SubscriptionRegistry:
  def create: IO[SubscriptionRegistry] =
    Ref
      .of[IO, Set[(ConsumerArn, String)]](Set.empty)
      .map(new SubscriptionRegistry(_))
