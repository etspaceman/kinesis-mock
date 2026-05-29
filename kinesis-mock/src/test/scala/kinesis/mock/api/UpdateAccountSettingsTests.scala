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
  test("It should update the minimum throughput billing commitment"):
    for
      streamsRef <- Ref.of[IO, Streams](Streams.empty)
      _ <- UpdateAccountSettingsRequest(Some(100))
        .updateAccountSettings(streamsRef)
      res <- DescribeAccountSettingsRequest().describeAccountSettings(streamsRef)
    yield assertEquals(res, DescribeAccountSettingsResponse(Some(100)))

  test("It should clear the minimum throughput billing commitment when None"):
    for
      streamsRef <- Ref.of[IO, Streams](
        Streams.empty.copy(accountSettings = AccountSettings(Some(50)))
      )
      _ <- UpdateAccountSettingsRequest(None).updateAccountSettings(streamsRef)
      res <- DescribeAccountSettingsRequest().describeAccountSettings(streamsRef)
    yield assertEquals(res, DescribeAccountSettingsResponse(None))

  test("It should reject a negative value with InvalidArgumentException"):
    for
      streamsRef <- Ref.of[IO, Streams](Streams.empty)
      res <- UpdateAccountSettingsRequest(Some(-1))
        .updateAccountSettings(streamsRef)
    yield assert(
      res.left.exists(_.isInstanceOf[InvalidArgumentException]),
      s"res: $res"
    )
