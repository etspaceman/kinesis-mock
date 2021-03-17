package kinesis.mock.models

import java.time.Instant

import io.circe._
import cats.kernel.Eq

final case class KinesisRecord(
    approximateArrivalTimestamp: Instant,
    data: Array[Byte],
    encryptionType: EncryptionType,
    partitionKey: String,
    sequenceNumber: SequenceNumber
) {
  val size = data.length +
    encryptionType.entryName.getBytes("UTF-8").length +
    partitionKey.getBytes("UTF-8").length +
    sequenceNumber.value.getBytes("UTF-8").length +
    approximateArrivalTimestamp.toString().getBytes("UTF-8").length
}

object KinesisRecord {
  implicit val kinesisRecordCirceEncoder: Encoder[KinesisRecord] =
    Encoder.forProduct5(
      "ApproximateArrivalTimestamp",
      "Data",
      "EncryptionType",
      "PartitionKey",
      "SequenceNumber"
    )(x =>
      (
        x.approximateArrivalTimestamp,
        x.data,
        x.encryptionType,
        x.partitionKey,
        x.sequenceNumber
      )
    )

  implicit val kinesisRecordCirceDecoder: Decoder[KinesisRecord] =
    x =>
      for {
        approximateArrivalTimestamp <- x
          .downField("ApproximateArrivalTimestamp")
          .as[Instant]
        data <- x.downField("Data").as[Array[Byte]]
        encryptionType <- x.downField("EncryptionType").as[EncryptionType]
        partitionKey <- x.downField("PartitionKey").as[String]
        sequenceNumber <- x.downField("SequenceNumber").as[SequenceNumber]
      } yield KinesisRecord(
        approximateArrivalTimestamp,
        data,
        encryptionType,
        partitionKey,
        sequenceNumber
      )

      implicit val kinesisRecordEq: Eq[KinesisRecord] = (x, y) =>
        x.approximateArrivalTimestamp == y.approximateArrivalTimestamp &&
        x.data.sameElements(y.data) &&
        x.encryptionType == y.encryptionType &&
        x.partitionKey == y.partitionKey &&
        x.sequenceNumber == y.sequenceNumber
        
}
