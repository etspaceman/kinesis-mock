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

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStream.html
final case class DescribeStreamRequest(
    exclusiveStartShardId: Option[String],
    limit: Option[Int],
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
):
  def describeStream(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[DescribeStreamResponse]] =
    streamsRef.get.map(streams =>
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
                    exclusiveStartShardId match
                      case Some(shardId) =>
                        CommonValidations.validateShardId(shardId)
                      case None => Right(())
                    ,
                    limit match
                      case Some(l) => CommonValidations.validateLimit(l)
                      case _       => Right(())
                  ).mapN((_, _) =>
                    DescribeStreamResponse(
                      StreamDescription
                        .fromStreamData(stream, exclusiveStartShardId, limit)
                    )
                  )
                )
            )
        }
    )

object DescribeStreamRequest:
  given describeStreamRequestCirceEncoder
      : circe.Encoder[DescribeStreamRequest] =
    circe.Encoder.forProduct4(
      "ExclusiveStartShardId",
      "Limit",
      "StreamName",
      "StreamARN"
    )(x => (x.exclusiveStartShardId, x.limit, x.streamName, x.streamArn))
  given describeStreamRequestCirceDecoder
      : circe.Decoder[DescribeStreamRequest] = x =>
    for
      exclusiveStartShardId <- x
        .downField("ExclusiveStartShardId")
        .as[Option[String]]
      limit <- x.downField("Limit").as[Option[Int]]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    yield DescribeStreamRequest(
      exclusiveStartShardId,
      limit,
      streamName,
      streamArn
    )
  given describeStreamRequestEncoder: Encoder[DescribeStreamRequest] =
    Encoder.derive
  given describeStreamRequestDecoder: Decoder[DescribeStreamRequest] =
    Decoder.derive
  given Eq[DescribeStreamRequest] =
    Eq.fromUniversalEquals
