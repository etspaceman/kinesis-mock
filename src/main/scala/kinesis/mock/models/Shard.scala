package kinesis.mock.models

import java.time.Instant

import cats.effect.concurrent.Semaphore
import cats.effect.{Concurrent, IO}
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
)(implicit C: Concurrent[IO]) {
  val numericShardId: Int = shardId.takeRight(12).toInt
  val isOpen: Boolean = sequenceNumberRange.endingSequenceNumber.isEmpty
  val semaphore = Semaphore[IO](1).unsafeRunSync()
}

object Shard {
  def shardId(shardIndex: Int): String =
    "shardId-" + s"00000000000$shardIndex".takeRight(12)
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

  implicit def shardCirceDecoder(implicit C: Concurrent[IO]): Decoder[Shard] = {
    x =>
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
}
