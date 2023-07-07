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

import java.time.Instant

import cats.Eq
import io.circe

import kinesis.mock.instances.circe._

final case class ShardFilter(
    shardId: Option[String],
    timestamp: Option[Instant],
    `type`: ShardFilterType
)

object ShardFilter {
  def shardFilterCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[ShardFilter] =
    circe.Encoder.forProduct3("ShardId", "Timestamp", "Type")(x =>
      (x.shardId, x.timestamp, x.`type`)
    )

  def shardFilterCirceDecoder(implicit
      DI: circe.Decoder[Instant]
  ): circe.Decoder[ShardFilter] = x =>
    for {
      shardId <- x.downField("ShardId").as[Option[String]]
      timestamp <- x.downField("Timestamp").as[Option[Instant]]
      `type` <- x.downField("Type").as[ShardFilterType]
    } yield ShardFilter(shardId, timestamp, `type`)

  implicit val shardFilterEncoder: Encoder[ShardFilter] = Encoder.instance(
    shardFilterCirceEncoder(instantBigDecimalCirceEncoder),
    shardFilterCirceEncoder(instantLongCirceEncoder)
  )
  implicit val shardFilterDecoder: Decoder[ShardFilter] = Decoder.instance(
    shardFilterCirceDecoder(instantBigDecimalCirceDecoder),
    shardFilterCirceDecoder(instantLongCirceDecoder)
  )

  implicit val shardFilterEq: Eq[ShardFilter] = (x, y) =>
    x.shardId == y.shardId &&
      x.timestamp.map(_.getEpochSecond()) == y.timestamp.map(
        _.getEpochSecond()
      ) &&
      x.`type` == y.`type`
}
