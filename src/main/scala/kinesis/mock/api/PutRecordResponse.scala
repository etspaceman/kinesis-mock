package kinesis.mock.api

import io.circe._

import kinesis.mock.models._

final case class PutRecordResponse(
    encryptionType: EncryptionType,
    sequenceNumber: SequenceNumber,
    shardId: String
)

object PutRecordResponse {
  implicit val putRecordResponseCirceEncoder: Encoder[PutRecordResponse] =
    Encoder.forProduct3("EncryptionType", "SequenceNumber", "ShardId")(x =>
      (x.encryptionType, x.sequenceNumber, x.shardId)
    )

  implicit val putRecordResponseCirceDecoder: Decoder[PutRecordResponse] =
    x =>
      for {
        encryptionType <- x.downField("EncryptionType").as[EncryptionType]
        sequenceNumber <- x.downField("SequenceNumber").as[SequenceNumber]
        shardId <- x.downField("ShardId").as[String]
      } yield PutRecordResponse(encryptionType, sequenceNumber, shardId)
}
