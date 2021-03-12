package kinesis.mock.api

import io.circe._

final case class GetShardIteratorResponse(shardIterator: ShardIterator)

object GetShardIteratorResponse {
  implicit val getShardIteratorResponseCirceEncoder
      : Encoder[GetShardIteratorResponse] =
    Encoder.forProduct1("ShardIterator")(_.shardIterator)
  implicit val getShardIteratorResponseCirceDecoder
      : Decoder[GetShardIteratorResponse] =
    _.downField("ShardIterator")
      .as[ShardIterator]
      .map(GetShardIteratorResponse.apply)
}
