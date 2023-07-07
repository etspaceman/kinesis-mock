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

import cats.Eq
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
