package kinesis.mock
package api

import cats.kernel.Eq
import io.circe

import kinesis.mock.models.ShardIterator

final case class GetShardIteratorResponse(shardIterator: ShardIterator)

object GetShardIteratorResponse {
  implicit val getShardIteratorResponseCirceEncoder
      : circe.Encoder[GetShardIteratorResponse] =
    circe.Encoder.forProduct1("ShardIterator")(_.shardIterator)
  implicit val getShardIteratorResponseCirceDecoder
      : circe.Decoder[GetShardIteratorResponse] =
    _.downField("ShardIterator")
      .as[ShardIterator]
      .map(GetShardIteratorResponse.apply)
  implicit val getShardIteratorResponseEncoder
      : Encoder[GetShardIteratorResponse] = Encoder.derive
  implicit val getShardIteratorResponseDecoder
      : Decoder[GetShardIteratorResponse] = Decoder.derive
  implicit val getShardIteratorResponseEq: Eq[GetShardIteratorResponse] =
    Eq.fromUniversalEquals
}
