/*
 * Copyright 2021-2026 io.github.etspaceman
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

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UpdateStreamWarmThroughput.html
final case class UpdateStreamWarmThroughputRequest(
    streamName: Option[StreamName],
    streamArn: Option[StreamArn],
    warmThroughputMiBps: Int
):
  def updateStreamWarmThroughput(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[UpdateStreamWarmThroughputResponse]] =
    streamsRef.modify { streams =>
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          (
            CommonValidations.validateStreamName(name),
            CommonValidations.findStream(arn, streams),
            if warmThroughputMiBps < 0 then
              InvalidArgumentException(
                "WarmThroughputMiBps must be non-negative"
              ).asLeft
            else Right(())
          ).mapN { (_, stream, _) =>
            (stream, ())
          }.flatMap { case (stream, _) =>
            if stream.streamModeDetails.streamMode === StreamMode.ON_DEMAND
            then
              Right(
                (
                  streams.updateStream(
                    stream.copy(warmThroughputMiBps = Some(warmThroughputMiBps))
                  ),
                  UpdateStreamWarmThroughputResponse(
                    streamName,
                    streamArn,
                    WarmThroughput(
                      currentMiBps = stream.warmThroughputMiBps
                        .getOrElse(warmThroughputMiBps),
                      targetMiBps = warmThroughputMiBps
                    )
                  )
                )
              )
            else
              InvalidArgumentException(
                "WarmThroughputMiBps is only valid for on-demand streams"
              ).asLeft
          }
        }
        .sequenceWithDefault(streams)
    }

object UpdateStreamWarmThroughputRequest:
  given updateStreamWarmThroughputRequestCirceEncoder
      : circe.Encoder[UpdateStreamWarmThroughputRequest] =
    circe.Encoder.forProduct3(
      "StreamName",
      "StreamARN",
      "WarmThroughputMiBps"
    )(x => (x.streamName, x.streamArn, x.warmThroughputMiBps))

  given updateStreamWarmThroughputRequestCirceDecoder
      : circe.Decoder[UpdateStreamWarmThroughputRequest] = x =>
    for
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      warmThroughputMiBps <- x.downField("WarmThroughputMiBps").as[Int]
    yield UpdateStreamWarmThroughputRequest(
      streamName,
      streamArn,
      warmThroughputMiBps
    )

  given updateStreamWarmThroughputRequestEncoder
      : Encoder[UpdateStreamWarmThroughputRequest] =
    Encoder.derive
  given updateStreamWarmThroughputRequestDecoder
      : Decoder[UpdateStreamWarmThroughputRequest] =
    Decoder.derive

  given Eq[UpdateStreamWarmThroughputRequest] =
    Eq.fromUniversalEquals
