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

import kinesis.mock.models.StreamDescriptionSummary

final case class DescribeStreamSummaryResponse(
    streamDescriptionSummary: StreamDescriptionSummary
)

object DescribeStreamSummaryResponse:
  def describeStreamSummaryResponseCirceEncoder(implicit
      ESDS: circe.Encoder[StreamDescriptionSummary]
  ): circe.Encoder[DescribeStreamSummaryResponse] =
    circe.Encoder.forProduct1("StreamDescriptionSummary")(
      _.streamDescriptionSummary
    )

  def describeStreamSummaryResponseCirceDecoder(implicit
      DSDS: circe.Decoder[StreamDescriptionSummary]
  ): circe.Decoder[DescribeStreamSummaryResponse] =
    _.downField("StreamDescriptionSummary")
      .as[StreamDescriptionSummary]
      .map(DescribeStreamSummaryResponse.apply)

  given describeStreamSummaryResponseEncoder
      : Encoder[DescribeStreamSummaryResponse] = Encoder.instance(
    describeStreamSummaryResponseCirceEncoder(
      Encoder[StreamDescriptionSummary].circeEncoder
    ),
    describeStreamSummaryResponseCirceEncoder(
      Encoder[StreamDescriptionSummary].circeCborEncoder
    )
  )
  given describeStreamSummaryResponseDecoder
      : Decoder[DescribeStreamSummaryResponse] = Decoder.instance(
    describeStreamSummaryResponseCirceDecoder(
      Decoder[StreamDescriptionSummary].circeDecoder
    ),
    describeStreamSummaryResponseCirceDecoder(
      Decoder[StreamDescriptionSummary].circeCborDecoder
    )
  )
  given describeStreamSummaryResponseEq: Eq[DescribeStreamSummaryResponse] =
    (x, y) => x.streamDescriptionSummary === y.streamDescriptionSummary
