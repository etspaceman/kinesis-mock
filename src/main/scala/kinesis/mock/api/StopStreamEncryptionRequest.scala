package kinesis.mock
package api

import cats.data.Validated._
import cats.data._
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class StopStreamEncryptionRequest(
    encryptionType: EncryptionType,
    keyId: String,
    streamName: StreamName
) {
  def stopStreamEncryption(
      streams: Streams
  ): ValidatedNel[KinesisMockException, Streams] =
    CommonValidations
      .findStream(streamName, streams)
      .andThen(stream =>
        (
          CommonValidations.validateStreamName(streamName),
          CommonValidations.validateKeyId(keyId),
          CommonValidations.isKmsEncryptionType(encryptionType),
          CommonValidations.isStreamActive(streamName, streams)
        ).mapN((_, _, _, _) =>
          streams.updateStream(
            stream.copy(
              encryptionType = EncryptionType.NONE,
              streamStatus = StreamStatus.UPDATING,
              keyId = None
            )
          )
        )
      )
}

object StopStreamEncryptionRequest {
  implicit val stopStreamEncryptionRequestCirceEncoder
      : Encoder[StopStreamEncryptionRequest] =
    Encoder.forProduct3("EncryptionType", "KeyId", "StreamName")(x =>
      (x.encryptionType, x.keyId, x.streamName)
    )

  implicit val stopStreamEncryptionRequestCirceDecoder
      : Decoder[StopStreamEncryptionRequest] = x =>
    for {
      encryptionType <- x.downField("EncryptionType").as[EncryptionType]
      keyId <- x.downField("KeyId").as[String]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield StopStreamEncryptionRequest(encryptionType, keyId, streamName)

  implicit val stopStreamEncryptionRequestEq: Eq[StopStreamEncryptionRequest] =
    Eq.fromUniversalEquals
}
