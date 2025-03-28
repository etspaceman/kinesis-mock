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

final case class StartStreamEncryptionRequest(
    encryptionType: EncryptionType,
    keyId: String,
    streamName: Option[StreamName],
    streamArn: Option[StreamArn]
) {
  def startStreamEncryption(
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
                  encryptionType = encryptionType,
                  streamStatus = StreamStatus.UPDATING,
                  keyId = Some(keyId)
                )
              ),
              ()
            )
          )
      }
      .sequenceWithDefault(streams)
  }
}

object StartStreamEncryptionRequest {
  given startStreamEncryptionRequestCirceEncoder
      : circe.Encoder[StartStreamEncryptionRequest] =
    circe.Encoder.forProduct4(
      "EncryptionType",
      "KeyId",
      "StreamName",
      "StreamARN"
    )(x => (x.encryptionType, x.keyId, x.streamName, x.streamArn))

  given startStreamEncryptionRequestCirceDecoder
      : circe.Decoder[StartStreamEncryptionRequest] = x =>
    for {
      encryptionType <- x.downField("EncryptionType").as[EncryptionType]
      keyId <- x.downField("KeyId").as[String]
      streamName <- x.downField("StreamName").as[Option[StreamName]]
      streamArn <- x.downField("StreamARN").as[Option[StreamArn]]
    } yield StartStreamEncryptionRequest(
      encryptionType,
      keyId,
      streamName,
      streamArn
    )

  given startStreamEncryptionRequestEncoder
      : Encoder[StartStreamEncryptionRequest] = Encoder.derive
  given startStreamEncryptionRequestDecoder
      : Decoder[StartStreamEncryptionRequest] = Decoder.derive

  given startStreamEncryptionRequestEq: Eq[StartStreamEncryptionRequest] =
    Eq.fromUniversalEquals
}
