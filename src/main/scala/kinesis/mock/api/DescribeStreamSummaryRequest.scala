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
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStreamSummary.html
final case class DescribeStreamSummaryRequest(
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def describeStreamSummary(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[DescribeStreamSummaryResponse]] =
    streamsRef.get.map(streams =>
      CommonValidations
        .getStreamNameArn(streamName, streamArn, awsRegion, awsAccountId)
        .flatMap { case (name, arn) =>
          CommonValidations
            .validateStreamName(name)
            .flatMap(_ =>
              CommonValidations
                .findStream(arn, streams)
                .map(stream =>
                  DescribeStreamSummaryResponse(
                    StreamDescriptionSummary.fromStreamData(stream)
                  )
                )
            )
        }
    )
}

object DescribeStreamSummaryRequest {
  implicit val describeStreamSummaryRequestCirceEncoder
      : circe.Encoder[DescribeStreamSummaryRequest] =
    circe.Encoder.forProduct2("StreamName", "StreamARN")(x =>
      (x.streamName, x.streamArn)
    )
  implicit val describeStreamSummaryRequestCirceDecoder
      : circe.Decoder[DescribeStreamSummaryRequest] = x =>
    for {
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    } yield DescribeStreamSummaryRequest(streamName, streamArn)
  implicit val describeStreamSummaryRequestEncoder
      : Encoder[DescribeStreamSummaryRequest] = Encoder.derive
  implicit val describeStreamSummaryRequestDecoder
      : Decoder[DescribeStreamSummaryRequest] = Decoder.derive
  implicit val describeStreamSummaryRequestEq
      : Eq[DescribeStreamSummaryRequest] = Eq.fromUniversalEquals
}
