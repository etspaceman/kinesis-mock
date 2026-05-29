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

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_UpdateMaxRecordSize.html
final case class UpdateMaxRecordSizeRequest(
    streamName: Option[StreamName],
    streamArn: Option[StreamArn],
    maxRecordSizeInKiB: Int
):
  def updateMaxRecordSize(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[UpdateMaxRecordSizeResponse]] =
    streamsRef.modify { streams =>
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          (
            CommonValidations.validateStreamName(name),
            CommonValidations.findStream(arn, streams),
            if maxRecordSizeInKiB < 1024 || maxRecordSizeInKiB > 10240 then
              InvalidArgumentException(
                "MaxRecordSizeInKiB must be between 1024 and 10240"
              ).asLeft
            else Right(())
          ).mapN { (_, stream, _) =>
            (
              streams.updateStream(
                stream.copy(maxRecordSizeInKiB = Some(maxRecordSizeInKiB))
              ),
              UpdateMaxRecordSizeResponse(
                streamName,
                streamArn,
                maxRecordSizeInKiB
              )
            )
          }
        }
        .sequenceWithDefault(streams)
    }

object UpdateMaxRecordSizeRequest:
  given updateMaxRecordSizeRequestCirceEncoder
      : circe.Encoder[UpdateMaxRecordSizeRequest] =
    circe.Encoder.forProduct3(
      "StreamName",
      "StreamARN",
      "MaxRecordSizeInKiB"
    )(x => (x.streamName, x.streamArn, x.maxRecordSizeInKiB))

  given updateMaxRecordSizeRequestCirceDecoder
      : circe.Decoder[UpdateMaxRecordSizeRequest] = x =>
    for
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
      maxRecordSizeInKiB <- x.downField("MaxRecordSizeInKiB").as[Int]
    yield UpdateMaxRecordSizeRequest(
      streamName,
      streamArn,
      maxRecordSizeInKiB
    )

  given updateMaxRecordSizeRequestEncoder
      : Encoder[UpdateMaxRecordSizeRequest] =
    Encoder.derive
  given updateMaxRecordSizeRequestDecoder
      : Decoder[UpdateMaxRecordSizeRequest] =
    Decoder.derive

  given Eq[UpdateMaxRecordSizeRequest] =
    Eq.fromUniversalEquals
