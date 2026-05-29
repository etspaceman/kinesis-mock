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
import io.circe

final case class DescribeAccountSettingsResponse(
    minimumThroughputBillingCommitment: Option[Int]
)

object DescribeAccountSettingsResponse:
  given describeAccountSettingsResponseCirceEncoder
      : circe.Encoder[DescribeAccountSettingsResponse] =
    circe.Encoder.forProduct1("MinimumThroughputBillingCommitment")(
      _.minimumThroughputBillingCommitment
    )
  given describeAccountSettingsResponseCirceDecoder
      : circe.Decoder[DescribeAccountSettingsResponse] = x =>
    x.downField("MinimumThroughputBillingCommitment")
      .as[Option[Int]]
      .map(DescribeAccountSettingsResponse(_))
  given describeAccountSettingsResponseEncoder
      : Encoder[DescribeAccountSettingsResponse] = Encoder.derive
  given describeAccountSettingsResponseDecoder
      : Decoder[DescribeAccountSettingsResponse] = Decoder.derive
  given Eq[DescribeAccountSettingsResponse] = Eq.fromUniversalEquals
