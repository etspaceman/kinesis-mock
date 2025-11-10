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
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*

final case class RegisterStreamConsumerResponse(consumer: ConsumerSummary)

object RegisterStreamConsumerResponse:
  def registerStreamConsumerResponseCirceEncoder(using
      EC: circe.Encoder[ConsumerSummary]
  ): circe.Encoder[RegisterStreamConsumerResponse] =
    circe.Encoder.forProduct1("Consumer")(_.consumer)
  def registerStreamConsumerResponseCirceDecoder(using
      DC: circe.Decoder[ConsumerSummary]
  ): circe.Decoder[RegisterStreamConsumerResponse] = _.downField("Consumer")
    .as[ConsumerSummary]
    .map(RegisterStreamConsumerResponse.apply)
  given registerStreamConsumerResponseEncoder
      : Encoder[RegisterStreamConsumerResponse] = Encoder.instance(
    registerStreamConsumerResponseCirceEncoder(using
      Encoder[ConsumerSummary].circeEncoder
    ),
    registerStreamConsumerResponseCirceEncoder(using
      Encoder[ConsumerSummary].circeCborEncoder
    )
  )
  given registerStreamConsumerResponseDecoder
      : Decoder[RegisterStreamConsumerResponse] = Decoder.instance(
    registerStreamConsumerResponseCirceDecoder(using
      Decoder[ConsumerSummary].circeDecoder
    ),
    registerStreamConsumerResponseCirceDecoder(using
      Decoder[ConsumerSummary].circeCborDecoder
    )
  )
  given registerStreamConsumerResponseEq: Eq[RegisterStreamConsumerResponse] =
    (x, y) => x.consumer === y.consumer
