package kinesis.mock

import cats.Eq

import kinesis.mock.api.{PutRecordRequest, PutRecordsRequestEntry}
import kinesis.mock.models.KinesisRecord

final case class PutRecordResults(
    data: Array[Byte],
    partitionKey: String
)

object PutRecordResults {
  def fromKinesisRecord(x: KinesisRecord): PutRecordResults =
    PutRecordResults(x.data, x.partitionKey)

  def fromPutRecordsRequestEntry(x: PutRecordsRequestEntry): PutRecordResults =
    PutRecordResults(x.data, x.partitionKey)

  def fromPutRecordRequest(x: PutRecordRequest): PutRecordResults =
    PutRecordResults(x.data, x.partitionKey)

  implicit val putRecordResultsEq: Eq[PutRecordResults] = (x, y) =>
    x.data.sameElements(y.data) && x.partitionKey == y.partitionKey
}
