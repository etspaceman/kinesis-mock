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

import kinesis.mock.models.StreamDescription

final case class DescribeStreamResponse(streamDescription: StreamDescription)

object DescribeStreamResponse:
  def describeStreamResponseCirceEncoder(using
      ESD: circe.Encoder[StreamDescription]
  ): circe.Encoder[DescribeStreamResponse] =
    circe.Encoder.forProduct1("StreamDescription")(_.streamDescription)

  def describeStreamResponseCirceDecoder(using
      DSD: circe.Decoder[StreamDescription]
  ): circe.Decoder[DescribeStreamResponse] =
    _.downField("StreamDescription")
      .as[StreamDescription]
      .map(DescribeStreamResponse.apply)
  given describeStreamResponseEncoder: Encoder[DescribeStreamResponse] =
    Encoder.instance(
      describeStreamResponseCirceEncoder(using
        Encoder[StreamDescription].circeEncoder
      ),
      describeStreamResponseCirceEncoder(using
        Encoder[StreamDescription].circeCborEncoder
      )
    )
  given describeStreamResponseDecoder: Decoder[DescribeStreamResponse] =
    Decoder.instance(
      describeStreamResponseCirceDecoder(using
        Decoder[StreamDescription].circeDecoder
      ),
      describeStreamResponseCirceDecoder(using
        Decoder[StreamDescription].circeCborDecoder
      )
    )
  given Eq[DescribeStreamResponse] =
    (x, y) => x.streamDescription === y.streamDescription
