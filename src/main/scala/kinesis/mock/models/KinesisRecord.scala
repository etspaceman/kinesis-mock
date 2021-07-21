package kinesis.mock
package models

import java.time.Instant

import cats.Eq
import io.circe

import kinesis.mock.instances.circe._

final case class KinesisRecord(
    approximateArrivalTimestamp: Instant,
    data: Array[Byte],
    encryptionType: EncryptionType,
    partitionKey: String,
    sequenceNumber: SequenceNumber
) {
  val size: Int = data.length +
    encryptionType.entryName.getBytes("UTF-8").length +
    partitionKey.getBytes("UTF-8").length +
    sequenceNumber.value.getBytes("UTF-8").length +
    approximateArrivalTimestamp.toString.getBytes("UTF-8").length
}

object KinesisRecord {
  def kinesisRecordCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[KinesisRecord] =
    circe.Encoder.forProduct5(
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

  def kinesisRecordCirceDecoder(implicit
      DI: circe.Decoder[Instant]
  ): circe.Decoder[KinesisRecord] =
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

  implicit val kinesisRecordEncoder: Encoder[KinesisRecord] = Encoder.instance(
    kinesisRecordCirceEncoder(instantDoubleCirceEncoder),
    kinesisRecordCirceEncoder(instantLongCirceEncoder)
  )

  implicit val kinesisRecordDecoder: Decoder[KinesisRecord] = Decoder.instance(
    kinesisRecordCirceDecoder(instantDoubleCirceDecoder),
    kinesisRecordCirceDecoder(instantLongCirceDecoder)
  )

  implicit val kinesisRecordEq: Eq[KinesisRecord] = (x, y) =>
    x.approximateArrivalTimestamp.getEpochSecond == y.approximateArrivalTimestamp.getEpochSecond &&
      x.data.sameElements(y.data) &&
      x.encryptionType == y.encryptionType &&
      x.partitionKey == y.partitionKey &&
      x.sequenceNumber == y.sequenceNumber

}
