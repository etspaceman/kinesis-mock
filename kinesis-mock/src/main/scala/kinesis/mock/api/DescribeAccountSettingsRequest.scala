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

import cats.Eq
import cats.effect.{IO, Ref}
import io.circe

import kinesis.mock.models.*

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeAccountSettings.html
final case class DescribeAccountSettingsRequest():
  def describeAccountSettings(
      streamsRef: Ref[IO, Streams]
  ): IO[DescribeAccountSettingsResponse] =
    streamsRef.get.map(streams =>
      DescribeAccountSettingsResponse(
        streams.accountSettings.minimumThroughputBillingCommitment
      )
    )

object DescribeAccountSettingsRequest:
  given describeAccountSettingsRequestCirceEncoder
      : circe.Encoder[DescribeAccountSettingsRequest] =
    circe.Encoder.instance(_ => circe.Json.obj())
  given describeAccountSettingsRequestCirceDecoder
      : circe.Decoder[DescribeAccountSettingsRequest] =
    circe.Decoder.const(DescribeAccountSettingsRequest())
  given describeAccountSettingsRequestEncoder
      : Encoder[DescribeAccountSettingsRequest] = Encoder.derive
  given describeAccountSettingsRequestDecoder
      : Decoder[DescribeAccountSettingsRequest] = Decoder.derive
  given Eq[DescribeAccountSettingsRequest] = Eq.fromUniversalEquals
