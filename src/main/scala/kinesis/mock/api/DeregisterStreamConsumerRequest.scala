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
import kinesis.mock.syntax.either.*
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DeregisterStreamConsumer.html
final case class DeregisterStreamConsumerRequest(
    consumerArn: Option[ConsumerArn],
    consumerName: Option[ConsumerName],
    streamArn: Option[StreamArn]
):
  private def deregister(
      streams: Streams,
      consumer: Consumer,
      stream: StreamData
  ): (Streams, Consumer) =
    val newConsumer =
      consumer.copy(consumerStatus = ConsumerStatus.DELETING)

    (
      streams.updateStream(
        stream.copy(consumers =
          stream.consumers ++ Seq(consumer.consumerName -> newConsumer)
        )
      ),
      newConsumer
    )

  def deregisterStreamConsumer(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Consumer]] = streamsRef.modify { streams =>
    (consumerArn, consumerName, streamArn) match
      case (Some(cArn), _, _) =>
        CommonValidations
          .findStreamByConsumerArn(cArn, streams)
          .flatMap {
            case (consumer, stream)
                if consumer.consumerStatus == ConsumerStatus.ACTIVE =>
              (consumer, stream).asRight
            case _ =>
              ResourceInUseException(
                s"Consumer $consumerName is not in an ACTIVE state"
              ).asLeft
          }
          .map { case (consumer, stream) =>
            deregister(streams, consumer, stream)
          }
          .sequenceWithDefault(streams)
      case (None, Some(cName), Some(sArn)) =>
        CommonValidations
          .findStream(sArn, streams)
          .flatMap { stream =>
            CommonValidations.findConsumer(cName, stream).flatMap {
              case consumer
                  if consumer.consumerStatus == ConsumerStatus.ACTIVE =>
                (consumer, stream).asRight
              case _ =>
                ResourceInUseException(
                  s"Consumer $consumerName is not in an ACTIVE state"
                ).asLeft

            }
          }
          .map { case (consumer, stream) =>
            deregister(streams, consumer, stream)
          }
          .sequenceWithDefault(streams)
      case _ =>
        (
          streams,
          InvalidArgumentException(
            "ConsumerArn or both ConsumerName and StreamARN are required for this request."
          ).asLeft
        )
  }

object DeregisterStreamConsumerRequest:
  given deregisterStreamConsumerRequestCirceEncoder
      : circe.Encoder[DeregisterStreamConsumerRequest] =
    circe.Encoder.forProduct3("ConsumerARN", "ConsumerName", "StreamARN")(x =>
      (x.consumerArn, x.consumerName, x.streamArn)
    )
  given deregisterStreamConsumerRequestCirceDecoder
      : circe.Decoder[DeregisterStreamConsumerRequest] = x =>
    for
      consumerArn <- x.downField("ConsumerARN").as[Option[ConsumerArn]]
      consumerName <- x.downField("ConsumerName").as[Option[ConsumerName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    yield DeregisterStreamConsumerRequest(
      consumerArn,
      consumerName,
      streamArn
    )
  given deregisterStreamConsumerRequestEncoder
      : Encoder[DeregisterStreamConsumerRequest] =
    Encoder.derive
  given deregisterStreamConsumerRequestDecoder
      : Decoder[DeregisterStreamConsumerRequest] =
    Decoder.derive
  given Eq[DeregisterStreamConsumerRequest] =
    (x, y) =>
      x.consumerArn === y.consumerArn &&
        x.consumerName === y.consumerName &&
        x.streamArn === y.streamArn
