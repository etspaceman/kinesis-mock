package kinesis.mock
package api

import cats.Eq
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

final case class StopStreamEncryptionRequest(
    encryptionType: EncryptionType,
    keyId: String,
    streamName: StreamName
) {
  def stopStreamEncryption(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Unit]] = streamsRef.modify(streams =>
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
      .sequenceWithDefault(streams)
  )
}

object StopStreamEncryptionRequest {
  implicit val stopStreamEncryptionRequestCirceEncoder
      : circe.Encoder[StopStreamEncryptionRequest] =
    circe.Encoder.forProduct3("EncryptionType", "KeyId", "StreamName")(x =>
      (x.encryptionType, x.keyId, x.streamName)
    )

  implicit val stopStreamEncryptionRequestCirceDecoder
      : circe.Decoder[StopStreamEncryptionRequest] = x =>
    for {
      encryptionType <- x.downField("EncryptionType").as[EncryptionType]
      keyId <- x.downField("KeyId").as[String]
      streamName <- x.downField("StreamName").as[StreamName]
    } yield StopStreamEncryptionRequest(encryptionType, keyId, streamName)

  implicit val stopStreamEncryptionRequestEncoder
      : Encoder[StopStreamEncryptionRequest] = Encoder.derive
  implicit val stopStreamEncryptionRequestDecoder
      : Decoder[StopStreamEncryptionRequest] = Decoder.derive

  implicit val stopStreamEncryptionRequestEq: Eq[StopStreamEncryptionRequest] =
    Eq.fromUniversalEquals
}
