package kinesis.mock.api

import io.circe._

import kinesis.mock.models.SequenceNumber

final case class PutRecordsResultEntry(
    errorCode: Option[PutRecordsErrorCode],
    errorMessage: Option[String],
    sequenceNumber: Option[SequenceNumber],
    shardId: Option[String]
)

object PutRecordsResultEntry {
  implicit val putRecordsResultEntryCirceEncoder
      : Encoder[PutRecordsResultEntry] =
    Encoder.forProduct4(
      "ErrorCode",
      "ErrorMessage",
      "SequenceNumber",
      "ShardId"
    )(x => (x.errorCode, x.errorMessage, x.sequenceNumber, x.shardId))

  implicit val putRecordsResultEntryCirceDecoder
      : Decoder[PutRecordsResultEntry] =
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
}
