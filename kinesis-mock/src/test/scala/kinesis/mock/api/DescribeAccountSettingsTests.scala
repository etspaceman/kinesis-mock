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

class DescribeAccountSettingsTests extends munit.CatsEffectSuite:
  test("It should return the default account settings"):
    for
      streamsRef <- Ref.of[IO, Streams](Streams.empty)
      res <- DescribeAccountSettingsRequest().describeAccountSettings(streamsRef)
    yield assertEquals(
      res,
      DescribeAccountSettingsResponse(None)
    )

  test("It should return the configured minimum throughput billing commitment"):
    for
      streamsRef <- Ref.of[IO, Streams](Streams.empty)
      _ <- streamsRef.update(
        _.copy(accountSettings =
          AccountSettings(minimumThroughputBillingCommitment = Some(100))
        )
      )
      res <- DescribeAccountSettingsRequest().describeAccountSettings(streamsRef)
    yield assertEquals(
      res,
      DescribeAccountSettingsResponse(Some(100))
    )
