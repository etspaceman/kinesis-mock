package kinesis.mock
package api

import cats.data.Validated._
import cats.effect.IO
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations
import cats.effect.Ref

final case class StopStreamEncryptionRequest(
    encryptionType: EncryptionType,
    keyId: String,
    streamName: StreamName
) {
  def stopStreamEncryption(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[Unit]] = streamsRef.get.flatMap(streams =>
    CommonValidations
      .validateStreamName(streamName)
      .andThen(_ =>
        CommonValidations
          .findStream(streamName, streams)
          .andThen(stream =>
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
              encryptionType = EncryptionType.NONE,
              streamStatus = StreamStatus.UPDATING,
              keyId = None
            )
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
