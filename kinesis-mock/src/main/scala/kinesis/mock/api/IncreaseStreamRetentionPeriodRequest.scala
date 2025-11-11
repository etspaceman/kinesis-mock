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

import scala.concurrent.duration.*

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*
import kinesis.mock.syntax.either.*
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_IncreaseStreamRetention.html
final case class IncreaseStreamRetentionPeriodRequest(
    retentionPeriodHours: Int,
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
):
  def increaseStreamRetention(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[Unit]] = streamsRef.modify { streams =>
    CommonValidations
      .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
      .flatMap { case (name, arn) =>
        CommonValidations
          .validateStreamName(name)
          .flatMap(_ =>
            CommonValidations
              .findStream(arn, streams)
              .flatMap(stream =>
                (
                  CommonValidations
                    .validateRetentionPeriodHours(retentionPeriodHours),
                  CommonValidations.isStreamActive(arn, streams),
                  if stream.retentionPeriod.toHours > retentionPeriodHours then
                    InvalidArgumentException(
                      s"Provided RetentionPeriodHours $retentionPeriodHours is less than the currently defined retention period ${stream.retentionPeriod.toHours}"
                    ).asLeft
                  else Right(())
                ).mapN((_, _, _) => stream)
              )
          )
          .map(stream =>
            (
              streams.updateStream(
                stream.copy(retentionPeriod = retentionPeriodHours.hours)
              ),
              ()
            )
          )
      }
      .sequenceWithDefault(streams)
  }

object IncreaseStreamRetentionPeriodRequest:
  given increaseStreamRetentionRequestCirceEncoder
      : circe.Encoder[IncreaseStreamRetentionPeriodRequest] =
    circe.Encoder.forProduct3(
      "RetentionPeriodHours",
      "StreamName",
      "StreamARN"
    )(x => (x.retentionPeriodHours, x.streamName, x.streamArn))
  given increaseStreamRetentionRequestCirceDecoder
      : circe.Decoder[IncreaseStreamRetentionPeriodRequest] = x =>
    for
      retentionPeriodHours <- x.downField("RetentionPeriodHours").as[Int]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    yield IncreaseStreamRetentionPeriodRequest(
      retentionPeriodHours,
      streamName,
      streamArn
    )
  given increaseStreamRetentionRequestEncoder
      : Encoder[IncreaseStreamRetentionPeriodRequest] = Encoder.derive
  given increaseStreamRetentionRequestDecoder
      : Decoder[IncreaseStreamRetentionPeriodRequest] = Decoder.derive
  given Eq[IncreaseStreamRetentionPeriodRequest] = Eq.fromUniversalEquals
