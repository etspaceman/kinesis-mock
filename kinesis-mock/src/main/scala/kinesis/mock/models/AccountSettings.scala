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

import cats.Eq
import io.circe

final case class AccountSettings(
    minimumThroughputBillingCommitment: Option[Int]
)

object AccountSettings:
  val default: AccountSettings = AccountSettings(None)

  given circe.Encoder[AccountSettings] =
    circe.Encoder.forProduct1("minimumThroughputBillingCommitment")(x =>
      x.minimumThroughputBillingCommitment
    )
  given circe.Decoder[AccountSettings] = x =>
    x.downField("minimumThroughputBillingCommitment")
      .as[Option[Int]]
      .map(AccountSettings(_))
  given Eq[AccountSettings] = Eq.fromUniversalEquals
