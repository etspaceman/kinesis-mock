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
package models

import java.time.Instant

import cats.Eq
import cats.syntax.all.*
import io.circe

import kinesis.mock.instances.circe.*

final case class ConsumerSummary(
    consumerArn: ConsumerArn,
    consumerCreationTimestamp: Instant,
    consumerName: ConsumerName,
    consumerStatus: ConsumerStatus
)

object ConsumerSummary:
  def fromConsumer(consumer: Consumer): ConsumerSummary = ConsumerSummary(
    consumer.consumerArn,
    consumer.consumerCreationTimestamp,
    consumer.consumerName,
    consumer.consumerStatus
  )
  def consumerSummaryCirceEncoder(using
      EI: circe.Encoder[Instant]
  ): circe.Encoder[ConsumerSummary] = circe.Encoder.forProduct4(
    "ConsumerARN",
    "ConsumerCreationTimestamp",
    "ConsumerName",
    "ConsumerStatus"
  )(x =>
    (
      x.consumerArn,
      x.consumerCreationTimestamp,
      x.consumerName,
      x.consumerStatus
    )
  )

  def consumerSummaryCirceDecoder(using
      DI: circe.Decoder[Instant]
  ): circe.Decoder[ConsumerSummary] = x =>
    for
      consumerArn <- x.downField("ConsumerARN").as[ConsumerArn]
      consumerCreationTimestamp <- x
        .downField("ConsumerCreationTimestamp")
        .as[Instant]
      consumerName <- x.downField("ConsumerName").as[ConsumerName]
      consumerStatus <- x.downField("ConsumerStatus").as[ConsumerStatus]
    yield ConsumerSummary(
      consumerArn,
      consumerCreationTimestamp,
      consumerName,
      consumerStatus
    )

  given consumerSummaryEncoder: Encoder[ConsumerSummary] =
    Encoder.instance(
      consumerSummaryCirceEncoder(using instantDoubleCirceEncoder),
      consumerSummaryCirceEncoder(using instantLongCirceEncoder)
    )

  given consumerSummaryDecoder: Decoder[ConsumerSummary] =
    Decoder.instance(
      consumerSummaryCirceDecoder(using instantDoubleCirceDecoder),
      consumerSummaryCirceDecoder(using instantLongCirceDecoder)
    )

  given consumerSummaryEq: Eq[ConsumerSummary] = (x, y) =>
    x.consumerArn === y.consumerArn &&
      x.consumerCreationTimestamp.getEpochSecond === y.consumerCreationTimestamp.getEpochSecond &&
      x.consumerName === y.consumerName &&
      x.consumerStatus === y.consumerStatus
