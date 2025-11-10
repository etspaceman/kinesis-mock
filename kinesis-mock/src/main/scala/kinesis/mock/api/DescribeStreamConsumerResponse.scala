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

import kinesis.mock.models.Consumer

final case class DescribeStreamConsumerResponse(consumerDescription: Consumer)

object DescribeStreamConsumerResponse:
  def describeStreamConsumerResponseCirceEncoder(using
      EC: circe.Encoder[Consumer]
  ): circe.Encoder[DescribeStreamConsumerResponse] =
    circe.Encoder.forProduct1("ConsumerDescription")(_.consumerDescription)

  def describeStreamConsumerResponseCirceDecoder(using
      DC: circe.Decoder[Consumer]
  ): circe.Decoder[DescribeStreamConsumerResponse] =
    _.downField("ConsumerDescription")
      .as[Consumer]
      .map(DescribeStreamConsumerResponse.apply)
  given describeStreamConsumerResponseEncoder
      : Encoder[DescribeStreamConsumerResponse] =
    Encoder.instance(
      describeStreamConsumerResponseCirceEncoder(using
        Encoder[Consumer].circeEncoder
      ),
      describeStreamConsumerResponseCirceEncoder(using
        Encoder[Consumer].circeCborEncoder
      )
    )
  given describeStreamConsumerResponseDecoder
      : Decoder[DescribeStreamConsumerResponse] =
    Decoder.instance(
      describeStreamConsumerResponseCirceDecoder(using
        Decoder[Consumer].circeDecoder
      ),
      describeStreamConsumerResponseCirceDecoder(using
        Decoder[Consumer].circeCborDecoder
      )
    )
  given Eq[DescribeStreamConsumerResponse] =
    (x, y) => x.consumerDescription === y.consumerDescription
