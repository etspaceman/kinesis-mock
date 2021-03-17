package kinesis.mock.models

import scala.collection.SortedMap

import java.time.Instant

import cats.kernel.Eq
import io.circe._

final case class Shard(
    adjacentParentShardId: Option[String],
    closedTimestamp: Option[Instant],
    createdAtTimestamp: Instant,
    hashKeyRange: HashKeyRange,
    parentShardId: Option[String],
    sequenceNumberRange: SequenceNumberRange,
    shardId: String,
    shardIndex: Int
) {
  val numericShardId: Int = shardId.takeRight(12).toInt
  val isOpen: Boolean = sequenceNumberRange.endingSequenceNumber.isEmpty
}

object Shard {

  val minHashKey: BigInt = BigInt(0)
  val maxHashKey: BigInt = BigInt("340282366920938463463374607431768211455")

  def shardId(shardIndex: Int): String =
    "shardId-" + s"00000000000$shardIndex".takeRight(12)

  def newShards(
      shardCount: Int,
      createTime: Instant,
      startingIndex: Int
  ): SortedMap[Shard, List[KinesisRecord]] = {
    val shardHash = maxHashKey / BigInt(shardCount)
    SortedMap.from(
      List
        .range(startingIndex, shardCount, 1)
        .map(index =>
          Shard(
            None,
            None,
            createTime,
            HashKeyRange(
              if (index < shardCount - 1) shardHash * BigInt(index + 1)
              else maxHashKey - BigInt(1),
              shardHash * BigInt(index)
            ),
            None,
            SequenceNumberRange(
              None,
              SequenceNumber.create(createTime, index, None, None, None)
            ),
            shardId(index),
            index
          ) -> List.empty
        )
    )
  }
  implicit val shardOrdering: Ordering[Shard] = new Ordering[Shard] {
    override def compare(x: Shard, y: Shard): Int =
      x.numericShardId.compare(y.numericShardId)
  }

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
      x.shardId,
      x.shardIndex
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
      shardId,
      shardIndex
    )
  }

  implicit val shardEq: Eq[Shard] = Eq.fromUniversalEquals
}
