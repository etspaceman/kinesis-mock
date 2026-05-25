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
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStreamConsumer.html
final case class DescribeStreamConsumerRequest(
    consumerArn: Option[ConsumerArn],
    consumerName: Option[ConsumerName],
    streamArn: Option[StreamArn]
):
  def describeStreamConsumer(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[DescribeStreamConsumerResponse]] =
    streamsRef.get.map { streams =>
      (consumerArn, consumerName, streamArn) match
        case (Some(cArn), _, _) =>
          CommonValidations.findStreamByConsumerArn(cArn, streams).map {
            case (consumer, _) => DescribeStreamConsumerResponse(consumer)
          }
        case (None, Some(cName), Some(sArn)) =>
          CommonValidations.findStream(sArn, streams).flatMap { stream =>
            CommonValidations
              .findConsumer(cName, stream)
              .map(DescribeStreamConsumerResponse.apply)
          }
        case _ =>
          InvalidArgumentException(
            "ConsumerArn or both ConsumerName and StreamARN are required for this request."
          ).asLeft
    }

object DescribeStreamConsumerRequest:
  given describeStreamConsumerRequestCirceEncoder
      : circe.Encoder[DescribeStreamConsumerRequest] =
    circe.Encoder.forProduct3("ConsumerARN", "ConsumerName", "StreamARN")(x =>
      (x.consumerArn, x.consumerName, x.streamArn)
    )
  given describeStreamConsumerRequestCirceDecoder
      : circe.Decoder[DescribeStreamConsumerRequest] = x =>
    for
      consumerArn <- x.downField("ConsumerARN").as[Option[ConsumerArn]]
      consumerName <- x.downField("ConsumerName").as[Option[ConsumerName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    yield DescribeStreamConsumerRequest(
      consumerArn,
      consumerName,
      streamArn
    )
  given describeStreamConsumerRequestEncoder
      : Encoder[DescribeStreamConsumerRequest] =
    Encoder.derive
  given describeStreamConsumerRequestDecoder
      : Decoder[DescribeStreamConsumerRequest] =
    Decoder.derive
  given Eq[DescribeStreamConsumerRequest] =
    (x, y) =>
      x.consumerArn === y.consumerArn &&
        x.consumerName === y.consumerName &&
        x.streamArn === y.streamArn
