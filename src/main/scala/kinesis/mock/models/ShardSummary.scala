package kinesis.mock
package models

import cats.kernel.Eq
import io.circe

final case class ShardSummary(
    adjacentParentShardId: Option[String],
    hashKeyRange: HashKeyRange,
    parentShardId: Option[String],
    sequenceNumberRange: SequenceNumberRange,
    shardId: String
) {
  val isOpen: Boolean = sequenceNumberRange.endingSequenceNumber.isEmpty
}

object ShardSummary {
  def fromShard(shard: Shard): ShardSummary = ShardSummary(
    shard.adjacentParentShardId,
    shard.hashKeyRange,
    shard.parentShardId,
    shard.sequenceNumberRange,
    shard.shardId.shardId
  )

  implicit val shardSummaryCirceEncoder: circe.Encoder[ShardSummary] =
    circe.Encoder.forProduct5(
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

  implicit val shardSummaryCirceDecoder: circe.Decoder[ShardSummary] = { x =>
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

  implicit val shardSummaryEncoder: Encoder[ShardSummary] = Encoder.derive
  implicit val shardSummaryDecoder: Decoder[ShardSummary] = Decoder.derive
  implicit val shardSummaryEq: Eq[ShardSummary] = Eq.fromUniversalEquals
}
