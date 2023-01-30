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
  ): IO[Response[Unit]] = {
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
}

object StopStreamEncryptionRequest {
  implicit val stopStreamEncryptionRequestCirceEncoder
      : circe.Encoder[StopStreamEncryptionRequest] =
    circe.Encoder.forProduct4(
      "EncryptionType",
      "KeyId",
      "StreamName",
      "StreamARN"
    )(x => (x.encryptionType, x.keyId, x.streamName, x.streamArn))

  implicit val stopStreamEncryptionRequestCirceDecoder
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

  implicit val stopStreamEncryptionRequestEncoder
      : Encoder[StopStreamEncryptionRequest] = Encoder.derive
  implicit val stopStreamEncryptionRequestDecoder
      : Decoder[StopStreamEncryptionRequest] = Decoder.derive

  implicit val stopStreamEncryptionRequestEq: Eq[StopStreamEncryptionRequest] =
    Eq.fromUniversalEquals
}
