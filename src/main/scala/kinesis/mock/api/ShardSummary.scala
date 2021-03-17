package kinesis.mock.api

import cats.kernel.Eq
import io.circe._

import kinesis.mock.models._

final case class ShardSummary(
    adjacentParentShardId: Option[String],
    hashKeyRange: HashKeyRange,
    parentShardId: Option[String],
    sequenceNumberRange: SequenceNumberRange,
    shardId: String
)

object ShardSummary {
  def fromShard(shard: Shard): ShardSummary = ShardSummary(
    shard.adjacentParentShardId,
    shard.hashKeyRange,
    shard.parentShardId,
    shard.sequenceNumberRange,
    shard.shardId
  )

  implicit val shardSummaryCirceEncoder: Encoder[ShardSummary] =
    Encoder.forProduct5(
      "AdjacentParentShardId",
      "HashKeyRange",
      "ParentShardId",
      "SequenceNumberRange",
      "ShardId"
    )(x =>
      (
        x.adjacentParentShardId,
        x.hashKeyRange,
        x.parentShardId,
        x.sequenceNumberRange,
        x.shardId
      )
    )

  implicit val shardSummaryCirceDecoder: Decoder[ShardSummary] = { x =>
    for {
      adjacentParentShardId <- x
        .downField("AdjacentParentShardId")
        .as[Option[String]]
      hashKeyRange <- x.downField("HashKeyRange").as[HashKeyRange]
      parentShardId <- x.downField("ParentShardId").as[Option[String]]
      sequenceNumberRange <- x
        .downField("SequenceNumberRange")
        .as[SequenceNumberRange]
      shardId <- x.downField("ShardId").as[String]
    } yield ShardSummary(
      adjacentParentShardId,
      hashKeyRange,
      parentShardId,
      sequenceNumberRange,
      shardId
    )
  }
  implicit val shardSummaryEq: Eq[ShardSummary] = Eq.fromUniversalEquals
}
