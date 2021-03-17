package kinesis.mock.api

import io.circe._
import cats.kernel.Eq

final case class PutRecordsRequestEntry(
    data: Array[Byte],
    explicitHashKey: Option[String],
    partitionKey: String
)

object PutRecordsRequestEntry {
  implicit val putRecordsRequestEntryCirceEncoder
      : Encoder[PutRecordsRequestEntry] =
    Encoder.forProduct3(
      "Data",
      "ExplicitHashKey",
      "PartitionKey"
    )(x => (x.data, x.explicitHashKey, x.partitionKey))

  implicit val putRecordsRequestEntryCirceDecoder
      : Decoder[PutRecordsRequestEntry] =
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

  implicit val putrecordsRequestEntryEq: Eq[PutRecordsRequestEntry] = (x, y) =>
    x.data.sameElements(y.data) &&
      x.explicitHashKey == y.explicitHashKey &&
      x.partitionKey == y.partitionKey
}
