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

final case class ChildShard(
    hashKeyRange: HashKeyRange,
    parentShards: Vector[String],
    shardId: String
)

object ChildShard:
  def fromShard(shard: Shard, parentShards: Vector[Shard]): ChildShard =
    ChildShard(
      shard.hashKeyRange,
      parentShards.map(_.shardId.shardId),
      shard.shardId.shardId
    )

  given childShardCirceEncoder: circe.Encoder[ChildShard] =
    circe.Encoder.forProduct3("HashKeyRange", "ParentShards", "ShardId")(x =>
      (x.hashKeyRange, x.parentShards, x.shardId)
    )

  given childShardCirceDecoder: circe.Decoder[ChildShard] = x =>
    for
      hashKeyRange <- x.downField("HashKeyRange").as[HashKeyRange]
      parentShards <- x.downField("ParentShards").as[Vector[String]]
      shardId <- x.downField("ShardId").as[String]
    yield ChildShard(hashKeyRange, parentShards, shardId)
  given childShardEncoder: Encoder[ChildShard] =
    Encoder.derive
  given childShardDecoder: Decoder[ChildShard] =
    Decoder.derive
  given childShardEq: Eq[ChildShard] = Eq.fromUniversalEquals
