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

package kinesis.mock
package api

import cats.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._

final case class RegisterStreamConsumerResponse(consumer: ConsumerSummary)

object RegisterStreamConsumerResponse {
  def registerStreamConsumerResponseCirceEncoder(implicit
      EC: circe.Encoder[ConsumerSummary]
  ): circe.Encoder[RegisterStreamConsumerResponse] =
    circe.Encoder.forProduct1("Consumer")(_.consumer)
  def registerStreamConsumerResponseCirceDecoder(implicit
      DC: circe.Decoder[ConsumerSummary]
  ): circe.Decoder[RegisterStreamConsumerResponse] = _.downField("Consumer")
    .as[ConsumerSummary]
    .map(RegisterStreamConsumerResponse.apply)
  implicit val registerStreamConsumerResponseEncoder
      : Encoder[RegisterStreamConsumerResponse] = Encoder.instance(
    registerStreamConsumerResponseCirceEncoder(
      Encoder[ConsumerSummary].circeEncoder
    ),
    registerStreamConsumerResponseCirceEncoder(
      Encoder[ConsumerSummary].circeCborEncoder
    )
  )
  implicit val registerStreamConsumerResponseDecoder
      : Decoder[RegisterStreamConsumerResponse] = Decoder.instance(
    registerStreamConsumerResponseCirceDecoder(
      Decoder[ConsumerSummary].circeDecoder
    ),
    registerStreamConsumerResponseCirceDecoder(
      Decoder[ConsumerSummary].circeCborDecoder
    )
  )
  implicit val registerStreamConsumerResponseEq
      : Eq[RegisterStreamConsumerResponse] = (x, y) => x.consumer === y.consumer
}
