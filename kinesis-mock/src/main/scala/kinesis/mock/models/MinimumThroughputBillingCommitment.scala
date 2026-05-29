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
package models

import java.time.Instant

import cats.Eq
import enumeratum.*
import io.circe

sealed trait MinimumThroughputBillingCommitmentStatus extends EnumEntry

object MinimumThroughputBillingCommitmentStatus
    extends Enum[MinimumThroughputBillingCommitmentStatus]
    with CirceEnum[MinimumThroughputBillingCommitmentStatus]
    with CatsEnum[MinimumThroughputBillingCommitmentStatus]:
  override val values: IndexedSeq[MinimumThroughputBillingCommitmentStatus] =
    findValues
  case object ENABLED extends MinimumThroughputBillingCommitmentStatus
  case object DISABLED extends MinimumThroughputBillingCommitmentStatus
  case object ENABLED_UNTIL_EARLIEST_ALLOWED_END
      extends MinimumThroughputBillingCommitmentStatus

final case class MinimumThroughputBillingCommitment(
    status: MinimumThroughputBillingCommitmentStatus,
    startedAt: Option[Instant] = None,
    endedAt: Option[Instant] = None,
    earliestAllowedEndAt: Option[Instant] = None
)

object MinimumThroughputBillingCommitment:
  def minimumThroughputBillingCommitmentCirceEncoder(using
      EI: circe.Encoder[Instant]
  ): circe.Encoder[MinimumThroughputBillingCommitment] =
    circe.Encoder.forProduct4(
      "Status",
      "StartedAt",
      "EndedAt",
      "EarliestAllowedEndAt"
    )(x => (x.status, x.startedAt, x.endedAt, x.earliestAllowedEndAt))

  def minimumThroughputBillingCommitmentCirceDecoder(using
      DI: circe.Decoder[Instant]
  ): circe.Decoder[MinimumThroughputBillingCommitment] = x =>
    for
      status <- x
        .downField("Status")
        .as[MinimumThroughputBillingCommitmentStatus]
      startedAt <- x.downField("StartedAt").as[Option[Instant]]
      endedAt <- x.downField("EndedAt").as[Option[Instant]]
      earliestAllowedEndAt <- x
        .downField("EarliestAllowedEndAt")
        .as[Option[Instant]]
    yield MinimumThroughputBillingCommitment(
      status,
      startedAt,
      endedAt,
      earliestAllowedEndAt
    )

  given minimumThroughputBillingCommitmentEncoder
      : Encoder[MinimumThroughputBillingCommitment] = Encoder.instance(
    minimumThroughputBillingCommitmentCirceEncoder(using
      instances.circe.instantDoubleCirceEncoder
    ),
    minimumThroughputBillingCommitmentCirceEncoder(using
      instances.circe.instantLongCirceEncoder
    )
  )

  given minimumThroughputBillingCommitmentDecoder
      : Decoder[MinimumThroughputBillingCommitment] = Decoder.instance(
    minimumThroughputBillingCommitmentCirceDecoder(using
      instances.circe.instantDoubleCirceDecoder
    ),
    minimumThroughputBillingCommitmentCirceDecoder(using
      instances.circe.instantLongCirceDecoder
    )
  )

  given Eq[MinimumThroughputBillingCommitment] = (x, y) =>
    x.status == y.status &&
      x.startedAt.map(_.toEpochMilli) == y.startedAt.map(_.toEpochMilli) &&
      x.endedAt.map(_.toEpochMilli) == y.endedAt.map(_.toEpochMilli) &&
      x.earliestAllowedEndAt.map(_.toEpochMilli) ==
      y.earliestAllowedEndAt.map(_.toEpochMilli)
