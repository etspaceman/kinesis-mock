package kinesis.mock
package models

import cats.Eq
import io.circe

final case class ChildShard(
    hashKeyRange: HashKeyRange,
    parentShards: List[String],
    shardId: String
)

object ChildShard {
  def fromShard(shard: Shard, parentShards: List[Shard]): ChildShard =
    ChildShard(
      shard.hashKeyRange,
      parentShards.map(_.shardId.shardId),
      shard.shardId.shardId
    )

  implicit val childShardCirceEncoder: circe.Encoder[ChildShard] =
    circe.Encoder.forProduct3("HashKeyRange", "ParentShards", "ShardId")(x =>
      (x.hashKeyRange, x.parentShards, x.shardId)
    )

  implicit val childShardCirceDecoder: circe.Decoder[ChildShard] = x =>
    for {
      hashKeyRange <- x.downField("HashKeyRange").as[HashKeyRange]
      parentShards <- x.downField("ParentShards").as[List[String]]
      shardId <- x.downField("ShardId").as[String]
    } yield ChildShard(hashKeyRange, parentShards, shardId)
  implicit val childShardEncoder: Encoder[ChildShard] =
    Encoder.derive
  implicit val childShardDecoder: Decoder[ChildShard] =
    Decoder.derive
  implicit val childShardEq: Eq[ChildShard] = Eq.fromUniversalEquals
}
