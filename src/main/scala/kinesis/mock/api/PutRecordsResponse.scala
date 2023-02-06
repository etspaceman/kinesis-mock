package kinesis.mock
package api

import cats.Eq
import io.circe

import kinesis.mock.models.EncryptionType

final case class PutRecordsResponse(
    encryptionType: EncryptionType,
    failedRecordCount: Option[Int],
    records: Vector[PutRecordsResultEntry]
)

object PutRecordsResponse {
  implicit val putRecordsResponseCirceEncoder
      : circe.Encoder[PutRecordsResponse] =
    circe.Encoder.forProduct3(
      "EncryptionType",
      "FailedRecordCount",
      "Records"
    )(x => (x.encryptionType, x.failedRecordCount, x.records))

  implicit val putRecordsResponseCirceDecoder
      : circe.Decoder[PutRecordsResponse] =
    x =>
      for {
        encryptionType <- x.downField("EncryptionType").as[EncryptionType]
        failedRecordCount <- x.downField("FailedRecordCount").as[Option[Int]]
        records <- x.downField("Records").as[Vector[PutRecordsResultEntry]]
      } yield PutRecordsResponse(encryptionType, failedRecordCount, records)

  implicit val putRecordsResponseEncoder: Encoder[PutRecordsResponse] =
    Encoder.derive
  implicit val putRecordsResponseDecoder: Decoder[PutRecordsResponse] =
    Decoder.derive

  implicit val putRecordsResponseEq: Eq[PutRecordsResponse] =
    Eq.fromUniversalEquals
}
