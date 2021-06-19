package kinesis.mock
package api

import cats.Eq
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

final case class StartStreamEncryptionRequest(
    encryptionType: EncryptionType,
    keyId: String,
    streamName: StreamName
) {
  def startStreamEncryption(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] = streamsRef.get.flatMap { streams =>
    CommonValidations
      .validateStreamName(streamName)
      .flatMap(_ =>
        CommonValidations
          .findStream(streamName, streams)
          .flatMap(stream =>
            (
              CommonValidations.validateKeyId(keyId),
              CommonValidations.isKmsEncryptionType(encryptionType),
              CommonValidations.isStreamActive(streamName, streams)
            ).mapN((_, _, _) => stream)
          )
      )
      .traverse(stream =>
        streamsRef.update(x =>
          x.updateStream(
            stream.copy(
              encryptionType = encryptionType,
              streamStatus = StreamStatus.UPDATING,
              keyId = Some(keyId)
            )
          )
        )
      )
  }
}

object StartStreamEncryptionRequest {
  implicit val startStreamEncryptionRequestCirceEncoder
      : circe.Encoder[StartStreamEncryptionRequest] =
    circe.Encoder.forProduct3("EncryptionType", "KeyId", "StreamName")(x =>
      (x.encryptionType, x.keyId, x.streamName)
    )

  implicit val startStreamEncryptionRequestCirceDecoder
      : circe.Decoder[StartStreamEncryptionRequest] = x =>
    for {
      encryptionType <- x.downField("EncryptionType").as[EncryptionType]
      keyId <- x.downField("KeyId").as[String]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield StartStreamEncryptionRequest(encryptionType, keyId, streamName)

  implicit val startStreamEncryptionRequestEncoder
      : Encoder[StartStreamEncryptionRequest] = Encoder.derive
  implicit val startStreamEncryptionRequestDecoder
      : Decoder[StartStreamEncryptionRequest] = Decoder.derive

  implicit val startStreamEncryptionRequestEq
      : Eq[StartStreamEncryptionRequest] = Eq.fromUniversalEquals
}
