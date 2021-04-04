package kinesis.mock.models

import scala.collection.SortedMap

import java.time.Instant

import cats.kernel.Eq
import io.circe._

import kinesis.mock.instances.circe._

final case class Shard(
    adjacentParentShardId: Option[String],
    closedTimestamp: Option[Instant],
    createdAtTimestamp: Instant,
    hashKeyRange: HashKeyRange,
    parentShardId: Option[String],
    sequenceNumberRange: SequenceNumberRange,
    shardId: ShardId
) {
  val isOpen: Boolean = sequenceNumberRange.endingSequenceNumber.isEmpty
}

object Shard {

  val minHashKey: BigInt = BigInt(0)
  val maxHashKey: BigInt = BigInt("340282366920938463463374607431768211455")

  def newShards(
      shardCount: Int,
      createTime: Instant,
      startingIndex: Int
  ): SortedMap[Shard, List[KinesisRecord]] = {
    val shardHash = maxHashKey / BigInt(shardCount)
    SortedMap.from(
      List
        .range(startingIndex, shardCount + startingIndex, 1)
        .zipWithIndex
        .map { case (shardIndex, listIndex) =>
          Shard(
            None,
            None,
            createTime,
            HashKeyRange(
              if (listIndex < shardCount - 1)
                (shardHash * BigInt(listIndex + 1)) - BigInt(1)
              else maxHashKey - BigInt(1),
              shardHash * BigInt(listIndex)
            ),
            None,
            SequenceNumberRange(
              None,
              SequenceNumber.create(createTime, shardIndex, None, None, None)
            ),
            ShardId.create(shardIndex)
          ) -> List.empty
        }
    )
  }
  implicit val shardOrdering: Ordering[Shard] = (x: Shard, y: Shard) =>
    Ordering[ShardId].compare(x.shardId, y.shardId)

  implicit val shardCirceEncoder: Encoder[Shard] = Encoder.forProduct8(
    "AdjacentParentShardId",
    "ClosedTimestamp",
    "CreatedAtTimestamp",
    "HashKeyRange",
    "ParentShardId",
    "SequenceNumberRange",
    "ShardId",
    "ShardIndex"
  )(x =>
    (
      x.adjacentParentShardId,
      x.closedTimestamp,
      x.createdAtTimestamp,
      x.hashKeyRange,
      x.parentShardId,
      x.sequenceNumberRange,
      x.shardId.shardId,
      x.shardId.index
    )
  )

  implicit val shardCirceDecoder: Decoder[Shard] = { x =>
    for {
      adjacentParentShardId <- x
        .downField("AdjacentParentShardId")
        .as[Option[String]]
      closedTimestamp <- x.downField("ClosedTimestamp").as[Option[Instant]]
      createdAtTimestamp <- x.downField("CreatedAtTimestamp").as[Instant]
      hashKeyRange <- x.downField("HashKeyRange").as[HashKeyRange]
      parentShardId <- x.downField("ParentShardId").as[Option[String]]
      sequenceNumberRange <- x
        .downField("SequenceNumberRange")
        .as[SequenceNumberRange]
      shardId <- x.downField("ShardId").as[String]
      shardIndex <- x.downField("ShardIndex").as[Int]
    } yield Shard(
      adjacentParentShardId,
      closedTimestamp,
      createdAtTimestamp,
      hashKeyRange,
      parentShardId,
      sequenceNumberRange,
      ShardId(shardId, shardIndex)
    )
  }

  implicit val shardEq: Eq[Shard] = (x, y) =>
    x.adjacentParentShardId == y.adjacentParentShardId &&
      x.closedTimestamp.map(_.getEpochSecond()) == y.closedTimestamp.map(
        _.getEpochSecond()
      ) &&
      x.createdAtTimestamp.getEpochSecond() == y.createdAtTimestamp
        .getEpochSecond() &&
      x.hashKeyRange == y.hashKeyRange &&
      x.parentShardId == y.parentShardId &&
      x.sequenceNumberRange == y.sequenceNumberRange &&
      x.shardId == y.shardId
}
