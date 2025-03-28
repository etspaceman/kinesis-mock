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

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UpdateStreamMode.html
final case class UpdateStreamModeRequest(
    streamArn: StreamArn,
    streamModeDetails: StreamModeDetails
):
  def updateStreamMode(
      streamsRef: Ref[IO, Streams],
      onDemandStreamCountLimit: Int
  ): IO[Response[Unit]] =
    streamsRef.modify { streams =>
      CommonValidations
        .validateStreamArn(streamArn)
        .flatMap(_ =>
          CommonValidations
            .findStream(streamArn, streams)
            .flatMap { stream =>
              (
                CommonValidations.isStreamActive(streamArn, streams),
                CommonValidations.validateOnDemandStreamCount(
                  streams,
                  onDemandStreamCountLimit
                )
              ).mapN((_, _) => stream)
            }
        )
        .map { stream =>
          (
            streams.updateStream(
              stream.copy(
                streamModeDetails = streamModeDetails,
                streamStatus = StreamStatus.UPDATING
              )
            ),
            ()
          )
        }
        .sequenceWithDefault(streams)
    }

object UpdateStreamModeRequest:
  given updateStreamModeRequestCirceEncoder
      : circe.Encoder[UpdateStreamModeRequest] =
    circe.Encoder.forProduct2("StreamARN", "StreamModeDetails")(x =>
      (x.streamArn, x.streamModeDetails)
    )

  given updateStreamModeRequestCirceDecoder
      : circe.Decoder[UpdateStreamModeRequest] = x =>
    for
      streamArn <- x.downField("StreamARN").as[StreamArn]
      streamModeDetails <- x
        .downField("StreamModeDetails")
        .as[StreamModeDetails]
    yield UpdateStreamModeRequest(streamArn, streamModeDetails)

  given updateStreamModeRequestEncoder: Encoder[UpdateStreamModeRequest] =
    Encoder.derive
  given updateStreamModeRequestDecoder: Decoder[UpdateStreamModeRequest] =
    Decoder.derive

  given updateStreamModeRequestEq: Eq[UpdateStreamModeRequest] =
    Eq.fromUniversalEquals
