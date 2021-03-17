package kinesis.mock.api

import io.circe._

import kinesis.mock.models.EncryptionType
import cats.kernel.Eq

final case class PutRecordsResponse(
    encryptionType: EncryptionType,
    failedRecordCount: Int,
    records: List[PutRecordsResultEntry]
)

object PutRecordsResponse {
  implicit val putRecordsResponseCirceEncdoer: Encoder[PutRecordsResponse] =
    Encoder.forProduct3(
      "EncryptionType",
      "FailedRecordCount",
      "Records"
    )(x => (x.encryptionType, x.failedRecordCount, x.records))

  implicit val putRecordsResponseCirceDecoder: Decoder[PutRecordsResponse] =
    x =>
      for {
        encryptionType <- x.downField("EncryptionType").as[EncryptionType]
        failedRecordCount <- x.downField("FailedRecordCount").as[Int]
        records <- x.downField("Records").as[List[PutRecordsResultEntry]]
      } yield PutRecordsResponse(encryptionType, failedRecordCount, records)

  implicit val putRecordsResponseEq: Eq[PutRecordsResponse] =
    Eq.fromUniversalEquals
}
