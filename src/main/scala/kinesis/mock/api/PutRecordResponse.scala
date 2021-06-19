package kinesis.mock
package api

import cats.Eq
import io.circe

import kinesis.mock.models._

final case class PutRecordResponse(
    encryptionType: EncryptionType,
    sequenceNumber: SequenceNumber,
    shardId: String
)

object PutRecordResponse {
  implicit val putRecordResponseCirceEncoder: circe.Encoder[PutRecordResponse] =
    circe.Encoder.forProduct3("EncryptionType", "SequenceNumber", "ShardId")(
      x => (x.encryptionType, x.sequenceNumber, x.shardId)
    )

  implicit val putRecordResponseCirceDecoder: circe.Decoder[PutRecordResponse] =
    x =>
      for {
        encryptionType <- x.downField("EncryptionType").as[EncryptionType]
        sequenceNumber <- x.downField("SequenceNumber").as[SequenceNumber]
        shardId <- x.downField("ShardId").as[String]
      } yield PutRecordResponse(encryptionType, sequenceNumber, shardId)

  implicit val putRecordResponseEncoder: Encoder[PutRecordResponse] =
    Encoder.derive
  implicit val putRecordResponseDecoder: Decoder[PutRecordResponse] =
    Decoder.derive

  implicit val putRecordResponseEq: Eq[PutRecordResponse] =
    Eq.fromUniversalEquals
}
