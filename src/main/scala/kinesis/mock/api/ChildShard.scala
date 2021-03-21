package kinesis.mock.api

import cats.kernel.Eq
import io.circe._

import kinesis.mock.models._

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

  implicit val childShardCirceEncoder: Encoder[ChildShard] =
    Encoder.forProduct3("HashKeyRange", "ParentShards", "ShardId")(x =>
      (x.hashKeyRange, x.parentShards, x.shardId)
    )

  implicit val childShardCirceDecoder: Decoder[ChildShard] = x =>
    for {
      hashKeyRange <- x.downField("HashKeyRange").as[HashKeyRange]
      parentShards <- x.downField("ParentShards").as[List[String]]
      shardId <- x.downField("ShardId").as[String]
    } yield ChildShard(hashKeyRange, parentShards, shardId)

  implicit val childShardEq: Eq[ChildShard] = Eq.fromUniversalEquals
}
