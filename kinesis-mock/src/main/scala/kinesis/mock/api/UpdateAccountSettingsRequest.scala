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

final case class MinimumThroughputBillingCommitmentInput(
    status: MinimumThroughputBillingCommitmentStatus
)

object MinimumThroughputBillingCommitmentInput:
  given circe.Encoder[MinimumThroughputBillingCommitmentInput] =
    circe.Encoder.forProduct1("Status")(_.status)
  given circe.Decoder[MinimumThroughputBillingCommitmentInput] = x =>
    x.downField("Status")
      .as[MinimumThroughputBillingCommitmentStatus]
      .map(MinimumThroughputBillingCommitmentInput(_))
  given Eq[MinimumThroughputBillingCommitmentInput] = Eq.fromUniversalEquals

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UpdateAccountSettings.html
final case class UpdateAccountSettingsRequest(
    minimumThroughputBillingCommitment: Option[
      MinimumThroughputBillingCommitmentInput
    ]
):
  def updateAccountSettings(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] = for
    now <- Utils.now
    _ <- streamsRef.update { streams =>
      val nextCommitment = minimumThroughputBillingCommitment.flatMap { input =>
        input.status match
          case MinimumThroughputBillingCommitmentStatus.ENABLED =>
            Some(
              MinimumThroughputBillingCommitment(
                status = MinimumThroughputBillingCommitmentStatus.ENABLED,
                startedAt = Some(now),
                endedAt = None,
                earliestAllowedEndAt = Some(now)
              )
            )
          case MinimumThroughputBillingCommitmentStatus.DISABLED => None
          case MinimumThroughputBillingCommitmentStatus.ENABLED_UNTIL_EARLIEST_ALLOWED_END =>
            streams.accountSettings.minimumThroughputBillingCommitment
      }
      streams.copy(accountSettings =
        streams.accountSettings.copy(
          minimumThroughputBillingCommitment = nextCommitment
        )
      )
    }
  yield Right(())

object UpdateAccountSettingsRequest:
  given updateAccountSettingsRequestCirceEncoder
      : circe.Encoder[UpdateAccountSettingsRequest] =
    circe.Encoder.forProduct1("MinimumThroughputBillingCommitment")(
      _.minimumThroughputBillingCommitment
    )
  given updateAccountSettingsRequestCirceDecoder
      : circe.Decoder[UpdateAccountSettingsRequest] = x =>
    x.downField("MinimumThroughputBillingCommitment")
      .as[Option[MinimumThroughputBillingCommitmentInput]]
      .map(UpdateAccountSettingsRequest(_))
  given updateAccountSettingsRequestEncoder
      : Encoder[UpdateAccountSettingsRequest] = Encoder.derive
  given updateAccountSettingsRequestDecoder
      : Decoder[UpdateAccountSettingsRequest] = Decoder.derive
  given Eq[UpdateAccountSettingsRequest] = Eq.fromUniversalEquals
