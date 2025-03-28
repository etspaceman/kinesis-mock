/*
 * Copyright 2021-2023 Typelevel
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

package kinesis.mock.models

import cats.kernel.Eq
import ciris.ConfigDecoder
import io.circe.Encoder

final case class AwsAccountId(accountId: String):
  override def toString: String = accountId

object AwsAccountId:
  given awsAccountIdCirceEncoder: Encoder[AwsAccountId] =
    Encoder[String].contramap(_.accountId)
  given awsAccountIdConfigDecoder: ConfigDecoder[String, AwsAccountId] =
    ConfigDecoder[String].map(AwsAccountId.apply)
  given awsAccountIdEq: Eq[AwsAccountId] = Eq.fromUniversalEquals
