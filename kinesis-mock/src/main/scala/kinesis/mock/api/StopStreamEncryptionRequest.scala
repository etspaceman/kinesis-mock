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
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

final case class StopStreamEncryptionRequest(
    encryptionType: EncryptionType,
    keyId: String,
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def stopStreamEncryption(
      streamsRef: Ref[IO, Streams],
      awsRegion: AwsRegion,
      awsAccountId: AwsAccountId
  ): IO[Response[Unit]] =
    streamsRef.modify(streams =>
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
                    CommonValidations.validateKeyId(keyId),
                    CommonValidations.isKmsEncryptionType(encryptionType),
                    CommonValidations.isStreamActive(arn, streams)
                  ).mapN((_, _, _) => stream)
                )
            )
            .map(stream =>
              (
                streams.updateStream(
                  stream.copy(
                    encryptionType = EncryptionType.NONE,
                    streamStatus = StreamStatus.UPDATING,
                    keyId = None
                  )
                ),
                ()
              )
            )
        }
        .sequenceWithDefault(streams)
    )
}

object StopStreamEncryptionRequest {
  given stopStreamEncryptionRequestCirceEncoder
      : circe.Encoder[StopStreamEncryptionRequest] =
    circe.Encoder.forProduct4(
      "EncryptionType",
      "KeyId",
      "StreamName",
      "StreamARN"
    )(x => (x.encryptionType, x.keyId, x.streamName, x.streamArn))

  given stopStreamEncryptionRequestCirceDecoder
      : circe.Decoder[StopStreamEncryptionRequest] = x =>
    for {
      encryptionType <- x.downField("EncryptionType").as[EncryptionType]
      keyId <- x.downField("KeyId").as[String]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    } yield StopStreamEncryptionRequest(
      encryptionType,
      keyId,
      streamName,
      streamArn
    )

  given stopStreamEncryptionRequestEncoder
      : Encoder[StopStreamEncryptionRequest] = Encoder.derive
  given stopStreamEncryptionRequestDecoder
      : Decoder[StopStreamEncryptionRequest] = Decoder.derive

  given stopStreamEncryptionRequestEq: Eq[StopStreamEncryptionRequest] =
    Eq.fromUniversalEquals
}
