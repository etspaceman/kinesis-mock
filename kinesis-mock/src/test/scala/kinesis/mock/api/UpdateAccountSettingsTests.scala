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
package api

import cats.effect.{IO, Ref}

import kinesis.mock.models.*

class UpdateAccountSettingsTests extends munit.CatsEffectSuite:
  test(
    "It should set the minimum throughput billing commitment when ENABLED"
  ):
    for
      streamsRef <- Ref.of[IO, Streams](Streams.empty)
      _ <- UpdateAccountSettingsRequest(
        Some(
          MinimumThroughputBillingCommitmentInput(
            MinimumThroughputBillingCommitmentStatus.ENABLED
          )
        )
      ).updateAccountSettings(streamsRef)
      res <- DescribeAccountSettingsRequest().describeAccountSettings(
        streamsRef
      )
    yield assertEquals(
      res.minimumThroughputBillingCommitment.map(_.status),
      Some(MinimumThroughputBillingCommitmentStatus.ENABLED)
    )

  test(
    "It should clear the minimum throughput billing commitment when DISABLED"
  ):
    for
      streamsRef <- Ref.of[IO, Streams](
        Streams.empty.copy(accountSettings =
          AccountSettings(
            Some(
              MinimumThroughputBillingCommitment(
                MinimumThroughputBillingCommitmentStatus.ENABLED
              )
            )
          )
        )
      )
      _ <- UpdateAccountSettingsRequest(
        Some(
          MinimumThroughputBillingCommitmentInput(
            MinimumThroughputBillingCommitmentStatus.DISABLED
          )
        )
      ).updateAccountSettings(streamsRef)
      res <- DescribeAccountSettingsRequest().describeAccountSettings(
        streamsRef
      )
    yield assertEquals(res, DescribeAccountSettingsResponse(None))
