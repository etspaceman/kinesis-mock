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
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*
import kinesis.mock.syntax.either.*

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UpdateAccountSettings.html
final case class UpdateAccountSettingsRequest(
    minimumThroughputBillingCommitment: Option[Int]
):
  def updateAccountSettings(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] = streamsRef.modify { streams =>
    (
      if minimumThroughputBillingCommitment.exists(_ < 0) then
        InvalidArgumentException(
          "MinimumThroughputBillingCommitment must be non-negative"
        ).asLeft
      else Right(())
    ).map(_ =>
      streams.copy(accountSettings =
        streams.accountSettings.copy(minimumThroughputBillingCommitment =
          minimumThroughputBillingCommitment
        )
      )
    ).map(updated => (updated, ()))
      .sequenceWithDefault(streams)
  }

object UpdateAccountSettingsRequest:
  given updateAccountSettingsRequestCirceEncoder
      : circe.Encoder[UpdateAccountSettingsRequest] =
    circe.Encoder.forProduct1("MinimumThroughputBillingCommitment")(
      _.minimumThroughputBillingCommitment
    )
  given updateAccountSettingsRequestCirceDecoder
      : circe.Decoder[UpdateAccountSettingsRequest] = x =>
    x.downField("MinimumThroughputBillingCommitment")
      .as[Option[Int]]
      .map(UpdateAccountSettingsRequest(_))
  given updateAccountSettingsRequestEncoder
      : Encoder[UpdateAccountSettingsRequest] = Encoder.derive
  given updateAccountSettingsRequestDecoder
      : Decoder[UpdateAccountSettingsRequest] = Decoder.derive
  given Eq[UpdateAccountSettingsRequest] = Eq.fromUniversalEquals
