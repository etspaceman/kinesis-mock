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

import scala.collection.SortedMap

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

final case class DeleteStreamRequest(
    streamName: Option[StreamName],
    streamArn: Option[StreamArn],
    enforceConsumerDeletion: Option[Boolean]
) {
  def deleteStream(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[Unit]] =
    streamsRef.modify { streams =>
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
                    CommonValidations.isStreamActive(arn, streams),
                    if (
                      !enforceConsumerDeletion
                        .getOrElse(false) && stream.consumers.nonEmpty
                    )
                      ResourceInUseException(
                        s"Consumers exist in stream $name and enforceConsumerDeletion is either not set or is false"
                      ).asLeft
                    else Right(())
                  ).mapN((_, _) => (stream, arn))
                )
            )
            .map { case (stream, arn) =>
              val deletingStream = Map(
                arn -> stream.copy(
                  shards = SortedMap.empty,
                  streamStatus = StreamStatus.DELETING,
                  tags = Tags.empty,
                  enhancedMonitoring = Vector.empty,
                  consumers = SortedMap.empty
                )
              )
              (
                streams.copy(
                  streams = streams.streams ++ deletingStream
                ),
                ()
              )
            }
        }
        .sequenceWithDefault(streams)
    }
}

object DeleteStreamRequest {
  given deleteStreamRequestCirceEncoder: circe.Encoder[DeleteStreamRequest] =
    circe.Encoder.forProduct3(
      "StreamName",
      "StreamARN",
      "EnforceConsumerDeletion"
    )(x => (x.streamName, x.streamArn, x.enforceConsumerDeletion))
  given deleteStreamRequestCirceDecoder: circe.Decoder[DeleteStreamRequest] = {
    x =>
      for {
        streamName <- x.downField("StreamName").as[Option[StreamName]]
        streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
        enforceConsumerDeletion <- x
          .downField("EnforceConsumerDeletion")
          .as[Option[Boolean]]
      } yield DeleteStreamRequest(
        streamName,
        streamArn,
        enforceConsumerDeletion
      )
  }
  given deleteStreamRequestEncoder: Encoder[DeleteStreamRequest] =
    Encoder.derive
  given deleteStreamRequestDecoder: Decoder[DeleteStreamRequest] =
    Decoder.derive
  given deleteStreamRequestEq: Eq[DeleteStreamRequest] =
    Eq.fromUniversalEquals
}
