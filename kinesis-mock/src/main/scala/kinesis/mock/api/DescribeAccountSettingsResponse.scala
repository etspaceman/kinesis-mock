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

import java.time.Instant

import cats.Eq
import io.circe

import kinesis.mock.instances.circe.*
import kinesis.mock.models.*

final case class DescribeAccountSettingsResponse(
    minimumThroughputBillingCommitment: Option[
      MinimumThroughputBillingCommitment
    ]
)

object DescribeAccountSettingsResponse:
  def describeAccountSettingsResponseCirceEncoder(using
      EI: circe.Encoder[Instant]
  ): circe.Encoder[DescribeAccountSettingsResponse] =
    given circe.Encoder[MinimumThroughputBillingCommitment] =
      MinimumThroughputBillingCommitment.minimumThroughputBillingCommitmentCirceEncoder
    circe.Encoder.forProduct1("MinimumThroughputBillingCommitment")(
      _.minimumThroughputBillingCommitment
    )

  def describeAccountSettingsResponseCirceDecoder(using
      DI: circe.Decoder[Instant]
  ): circe.Decoder[DescribeAccountSettingsResponse] =
    given circe.Decoder[MinimumThroughputBillingCommitment] =
      MinimumThroughputBillingCommitment.minimumThroughputBillingCommitmentCirceDecoder
    x =>
      x.downField("MinimumThroughputBillingCommitment")
        .as[Option[MinimumThroughputBillingCommitment]]
        .map(DescribeAccountSettingsResponse(_))

  given describeAccountSettingsResponseCirceEncoder
      : circe.Encoder[DescribeAccountSettingsResponse] =
    describeAccountSettingsResponseCirceEncoder(using instantDoubleCirceEncoder)

  given describeAccountSettingsResponseCirceDecoder
      : circe.Decoder[DescribeAccountSettingsResponse] =
    describeAccountSettingsResponseCirceDecoder(using instantDoubleCirceDecoder)

  given describeAccountSettingsResponseEncoder
      : Encoder[DescribeAccountSettingsResponse] = Encoder.instance(
    describeAccountSettingsResponseCirceEncoder(using
      instantDoubleCirceEncoder
    ),
    describeAccountSettingsResponseCirceEncoder(using instantLongCirceEncoder)
  )
  given describeAccountSettingsResponseDecoder
      : Decoder[DescribeAccountSettingsResponse] = Decoder.instance(
    describeAccountSettingsResponseCirceDecoder(using
      instantDoubleCirceDecoder
    ),
    describeAccountSettingsResponseCirceDecoder(using instantLongCirceDecoder)
  )
  given Eq[DescribeAccountSettingsResponse] = (x, y) =>
    (
      x.minimumThroughputBillingCommitment,
      y.minimumThroughputBillingCommitment
    ) match
      case (None, None)       => true
      case (Some(a), Some(b)) =>
        Eq[MinimumThroughputBillingCommitment].eqv(a, b)
      case _ => false
