package kinesis.mock.api

import cats.kernel.Eq
import cats.syntax.all._
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

  implicit val getRecordsResponseEq: Eq[GetRecordsResponse] = (x, y) =>
    x.childShards == y.childShards &&
      x.millisBehindLatest == y.millisBehindLatest &&
      x.nextShardIterator == y.nextShardIterator &&
      x.records === y.records
}
