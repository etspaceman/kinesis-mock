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
  implicit val startStreamEncryptionRequestCirceEncoder
      : circe.Encoder[StartStreamEncryptionRequest] =
    circe.Encoder.forProduct4(
      "EncryptionType",
      "KeyId",
      "StreamName",
      "StreamARN"
    )(x => (x.encryptionType, x.keyId, x.streamName, x.streamArn))

  implicit val startStreamEncryptionRequestCirceDecoder
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

  implicit val startStreamEncryptionRequestEncoder
      : Encoder[StartStreamEncryptionRequest] = Encoder.derive
  implicit val startStreamEncryptionRequestDecoder
      : Decoder[StartStreamEncryptionRequest] = Decoder.derive

  implicit val startStreamEncryptionRequestEq
      : Eq[StartStreamEncryptionRequest] = Eq.fromUniversalEquals
}
