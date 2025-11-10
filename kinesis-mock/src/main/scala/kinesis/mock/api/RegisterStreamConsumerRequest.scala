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

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_RegisterStreamConsumer.html
final case class RegisterStreamConsumerRequest(
    consumerName: ConsumerName,
    streamArn: StreamArn
):
  def registerStreamConsumer(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[RegisterStreamConsumerResponse]] =
    Utils.now.flatMap { now =>
      streamsRef.modify { streams =>
        CommonValidations
          .validateStreamArn(streamArn)
          .flatMap(_ =>
            CommonValidations
              .findStream(streamArn, streams)
              .flatMap(stream =>
                (
                  CommonValidations.validateConsumerName(consumerName),
                  if stream.consumers.size >= 20 then
                    LimitExceededException(
                      s"Only 20 consumers can be registered to a stream at once"
                    ).asLeft
                  else Right(()),
                  if stream.consumers.values
                      .count(_.consumerStatus == ConsumerStatus.CREATING) >= 5
                  then
                    LimitExceededException(
                      s"Only 5 consumers can be created at the same time"
                    ).asLeft
                  else Right(()),
                  if stream.consumers.contains(consumerName) then
                    ResourceInUseException(
                      s"Consumer $consumerName exists"
                    ).asLeft
                  else Right(())
                ).mapN((_, _, _, _) => (stream, streamArn, consumerName))
              )
          )
          .map { case (stream, streamArn, consumerName) =>
            val consumer = Consumer.create(streamArn, consumerName, now)

            (
              streams.updateStream(
                stream
                  .copy(consumers =
                    stream.consumers ++ Seq(consumerName -> consumer)
                  )
              ),
              RegisterStreamConsumerResponse(
                ConsumerSummary.fromConsumer(consumer)
              )
            )
          }
          .sequenceWithDefault(streams)
      }
    }

object RegisterStreamConsumerRequest:
  given registerStreamConsumerRequestCirceEncoder
      : circe.Encoder[RegisterStreamConsumerRequest] =
    circe.Encoder.forProduct2("ConsumerName", "StreamARN")(x =>
      (x.consumerName, x.streamArn)
    )
  given registerStreamConsumerRequestCirceDecoder
      : circe.Decoder[RegisterStreamConsumerRequest] = x =>
    for
      consumerName <- x.downField("ConsumerName").as[ConsumerName]
      streamArn <- x.downField("StreamARN").as[StreamArn]
    yield RegisterStreamConsumerRequest(consumerName, streamArn)
  given registerStreamConsumerRequestEncoder
      : Encoder[RegisterStreamConsumerRequest] = Encoder.derive
  given registerStreamConsumerRequestDecoder
      : Decoder[RegisterStreamConsumerRequest] = Decoder.derive
  given Eq[RegisterStreamConsumerRequest] =
    (x, y) => x.consumerName === y.consumerName && x.streamArn === y.streamArn
