package kinesis.mock
package api

import cats.Eq
import io.circe

import kinesis.mock.models.SequenceNumber

final case class PutRecordsResultEntry(
    errorCode: Option[PutRecordsErrorCode],
    errorMessage: Option[String],
    sequenceNumber: Option[SequenceNumber],
    shardId: Option[String]
)

object PutRecordsResultEntry {
  implicit val putRecordsResultEntryCirceEncoder
      : circe.Encoder[PutRecordsResultEntry] =
    circe.Encoder.forProduct4(
      "ErrorCode",
      "ErrorMessage",
      "SequenceNumber",
      "ShardId"
    )(x => (x.errorCode, x.errorMessage, x.sequenceNumber, x.shardId))

  implicit val putRecordsResultEntryCirceDecoder
      : circe.Decoder[PutRecordsResultEntry] =
    x =>
      for {
        errorCode <- x.downField("ErrorCode").as[Option[PutRecordsErrorCode]]
        errorMessage <- x.downField("ErrorMessage").as[Option[String]]
        sequenceNumber <- x
          .downField("SequenceNumber")
          .as[Option[SequenceNumber]]
        shardId <- x.downField("ShardId").as[Option[String]]
      } yield PutRecordsResultEntry(
        errorCode,
        errorMessage,
        sequenceNumber,
        shardId
      )
  implicit val putRecordsResultEntryEncoder: Encoder[PutRecordsResultEntry] =
    Encoder.derive
  implicit val putRecordsResultEntryDecoder: Decoder[PutRecordsResultEntry] =
    Decoder.derive

  implicit val putRecordsResultEntryEq: Eq[PutRecordsResultEntry] =
    Eq.fromUniversalEquals
}
