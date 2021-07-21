package kinesis.mock
package api

import scala.collection.immutable.Queue

import cats.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._

final case class GetRecordsResponse(
    childShards: Vector[ChildShard],
    millisBehindLatest: Long,
    nextShardIterator: ShardIterator,
    records: Queue[KinesisRecord]
)

object GetRecordsResponse {
  def getRecordsResponseCirceEncoder(implicit
      EKR: circe.Encoder[KinesisRecord]
  ): circe.Encoder[GetRecordsResponse] =
    circe.Encoder.forProduct4(
      "ChildShards",
      "MillisBehindLatest",
      "NextShardIterator",
      "Records"
    )(x =>
      (x.childShards, x.millisBehindLatest, x.nextShardIterator, x.records)
    )

  def getRecordsResponseCirceDecoder(implicit
      DKR: circe.Decoder[KinesisRecord]
  ): circe.Decoder[GetRecordsResponse] =
    x =>
      for {
        childShards <- x.downField("ChildShards").as[Vector[ChildShard]]
        millisBehindLatest <- x.downField("MillisBehindLatest").as[Long]
        nextShardIterator <- x.downField("NextShardIterator").as[ShardIterator]
        records <- x.downField("Records").as[Queue[KinesisRecord]]
      } yield GetRecordsResponse(
        childShards,
        millisBehindLatest,
        nextShardIterator,
        records
      )

  implicit val getRecordsResponseEncoder: Encoder[GetRecordsResponse] =
    Encoder.instance(
      getRecordsResponseCirceEncoder(Encoder[KinesisRecord].circeEncoder),
      getRecordsResponseCirceEncoder(Encoder[KinesisRecord].circeCborEncoder)
    )

  implicit val getRecordsResponseDecoder: Decoder[GetRecordsResponse] =
    Decoder.instance(
      getRecordsResponseCirceDecoder(Decoder[KinesisRecord].circeDecoder),
      getRecordsResponseCirceDecoder(Decoder[KinesisRecord].circeCborDecoder)
    )

  implicit val getRecordsResponseEq: Eq[GetRecordsResponse] = (x, y) =>
    x.childShards == y.childShards &&
      x.millisBehindLatest == y.millisBehindLatest &&
      x.nextShardIterator == y.nextShardIterator &&
      x.records === y.records
}
