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

import kinesis.mock.models.Consumer

final case class DescribeStreamConsumerResponse(consumerDescription: Consumer)

object DescribeStreamConsumerResponse {
  def describeStreamConsumerResponseCirceEncoder(implicit
      EC: circe.Encoder[Consumer]
  ): circe.Encoder[DescribeStreamConsumerResponse] =
    circe.Encoder.forProduct1("ConsumerDescription")(_.consumerDescription)

  def describeStreamConsumerResponseCirceDecoder(implicit
      DC: circe.Decoder[Consumer]
  ): circe.Decoder[DescribeStreamConsumerResponse] =
    _.downField("ConsumerDescription")
      .as[Consumer]
      .map(DescribeStreamConsumerResponse.apply)
  given describeStreamConsumerResponseEncoder
      : Encoder[DescribeStreamConsumerResponse] =
    Encoder.instance(
      describeStreamConsumerResponseCirceEncoder(
        Encoder[Consumer].circeEncoder
      ),
      describeStreamConsumerResponseCirceEncoder(
        Encoder[Consumer].circeCborEncoder
      )
    )
  given describeStreamConsumerResponseDecoder
      : Decoder[DescribeStreamConsumerResponse] =
    Decoder.instance(
      describeStreamConsumerResponseCirceDecoder(
        Decoder[Consumer].circeDecoder
      ),
      describeStreamConsumerResponseCirceDecoder(
        Decoder[Consumer].circeCborDecoder
      )
    )
  given describeStreamConsumerResponseEq: Eq[DescribeStreamConsumerResponse] =
    (x, y) => x.consumerDescription === y.consumerDescription
}
