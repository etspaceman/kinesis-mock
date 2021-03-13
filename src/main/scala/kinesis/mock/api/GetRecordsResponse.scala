package kinesis.mock.api

import io.circe._

import kinesis.mock.models.KinesisRecord

final case class GetRecordsResponse(
    childShards: List[ChildShard],
    millisBehindLatest: Long,
    nextShardIterator: ShardIterator,
    records: List[KinesisRecord]
)

object GetRecordsResponse {
  implicit val getRecordsResponseCirceEncoder: Encoder[GetRecordsResponse] =
    Encoder.forProduct4(
      "ChildShards",
      "MillisBehindLatest",
      "NextShardIterator",
      "Records"
    )(x =>
      (x.childShards, x.millisBehindLatest, x.nextShardIterator, x.records)
    )

  implicit val getRecordsResponseCirceDecoder: Decoder[GetRecordsResponse] =
    x =>
      for {
        childShards <- x.downField("ChildShards").as[List[ChildShard]]
        millisBehindLatest <- x.downField("ShardIterator").as[Long]
        nextShardIterator <- x.downField("NextShardIterator").as[ShardIterator]
        records <- x.downField("Records").as[List[KinesisRecord]]
      } yield GetRecordsResponse(
        childShards,
        millisBehindLatest,
        nextShardIterator,
        records
      )
}
