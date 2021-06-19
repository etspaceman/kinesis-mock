package kinesis.mock
package api

import cats.Eq
import io.circe

import kinesis.mock.instances.circe._

final case class PutRecordsRequestEntry(
    data: Array[Byte],
    explicitHashKey: Option[String],
    partitionKey: String
)

object PutRecordsRequestEntry {
  implicit val putRecordsRequestEntryCirceEncoder
      : circe.Encoder[PutRecordsRequestEntry] =
    circe.Encoder.forProduct3(
      "Data",
      "ExplicitHashKey",
      "PartitionKey"
    )(x => (x.data, x.explicitHashKey, x.partitionKey))

  implicit val putRecordsRequestEntryCirceDecoder
      : circe.Decoder[PutRecordsRequestEntry] =
    x =>
      for {
        data <- x.downField("Data").as[Array[Byte]]
        explicitHashKey <- x.downField("ExplicitHashKey").as[Option[String]]
        partitionKey <- x.downField("PartitionKey").as[String]
      } yield PutRecordsRequestEntry(
        data,
        explicitHashKey,
        partitionKey
      )

  implicit val putRecordsRequestEntryEncoder: Encoder[PutRecordsRequestEntry] =
    Encoder.derive
  implicit val putRecordsRequestEntryDecoder: Decoder[PutRecordsRequestEntry] =
    Decoder.derive

  implicit val putrecordsRequestEntryEq: Eq[PutRecordsRequestEntry] = (x, y) =>
    x.data.sameElements(y.data) &&
      x.explicitHashKey == y.explicitHashKey &&
      x.partitionKey == y.partitionKey
}
