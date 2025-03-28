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

import scala.math.Ordering

import cats.Eq
import io.circe.*

final case class ConsumerName(consumerName: String):
  override def toString: String = consumerName

object ConsumerName:
  given consumerNameOrdering: Ordering[ConsumerName] =
    (x: ConsumerName, y: ConsumerName) =>
      Ordering[String].compare(x.consumerName, y.consumerName)
  given consumerNameCirceEncoder: Encoder[ConsumerName] =
    Encoder[String].contramap(_.consumerName)
  given consumerNameCirceDecoder: Decoder[ConsumerName] =
    Decoder[String].map(ConsumerName.apply)
  given consumerNameCirceKeyEncoder: KeyEncoder[ConsumerName] =
    KeyEncoder[String].contramap(_.consumerName)
  given consumerNameCirceKeyDecoder: KeyDecoder[ConsumerName] =
    KeyDecoder[String].map(ConsumerName.apply)
  given consumerNameEq: Eq[ConsumerName] = Eq.fromUniversalEquals
