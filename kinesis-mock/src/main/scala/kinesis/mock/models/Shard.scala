/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock
package models

import scala.collection.SortedMap

import java.time.Instant

import cats.Eq
import io.circe
import io.circe.parser._
import io.circe.syntax._

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
  ): SortedMap[Shard, Vector[KinesisRecord]] = {
    val shardHash = maxHashKey / BigInt(shardCount)
    SortedMap.from(
      Vector
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
              else maxHashKey,
              shardHash * BigInt(listIndex)
            ),
            None,
            SequenceNumberRange(
              None,
              SequenceNumber.create(createTime, shardIndex, None, None, None)
            ),
            ShardId.create(shardIndex)
          ) -> Vector.empty
        }
    )
  }
  implicit val shardOrdering: Ordering[Shard] = (x: Shard, y: Shard) =>
    Ordering[ShardId].compare(x.shardId, y.shardId)

  def shardCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[Shard] = circe.Encoder.forProduct8(
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

  def shardCirceDecoder(implicit
      DI: circe.Decoder[Instant]
  ): circe.Decoder[Shard] = { x =>
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

  implicit val shardEncoder: Encoder[Shard] = Encoder.instance(
    shardCirceEncoder(instantDoubleCirceEncoder),
    shardCirceEncoder(instantLongCirceEncoder)
  )

  implicit val shardDecoder: Decoder[Shard] = Decoder.instance(
    shardCirceDecoder(instantDoubleCirceDecoder),
    shardCirceDecoder(instantLongCirceDecoder)
  )

  implicit val shardCirceKeyEncoder: circe.KeyEncoder[Shard] =
    circe
      .KeyEncoder[String]
      .contramap(x => shardCirceEncoder.apply(x).asJson.noSpaces)

  implicit val shardCirceKeyDecoder: circe.KeyDecoder[Shard] =
    circe.KeyDecoder.instance(x =>
      parse(x).flatMap(_.as[Shard](shardCirceDecoder)).toOption
    )

  implicit val shardEq: Eq[Shard] = (x, y) =>
    x.adjacentParentShardId == y.adjacentParentShardId &&
      x.closedTimestamp.map(_.getEpochSecond()) == y.closedTimestamp.map(
        _.getEpochSecond()
      ) &&
      x.createdAtTimestamp.getEpochSecond == y.createdAtTimestamp.getEpochSecond &&
      x.hashKeyRange == y.hashKeyRange &&
      x.parentShardId == y.parentShardId &&
      x.sequenceNumberRange == y.sequenceNumberRange &&
      x.shardId == y.shardId
}
