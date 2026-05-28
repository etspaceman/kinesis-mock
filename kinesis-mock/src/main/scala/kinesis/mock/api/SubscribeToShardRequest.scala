/*
 * Copyright 2021-2026 io.github.etspaceman
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
package api

import java.time.Instant

import cats.Eq
import cats.syntax.all.*
import io.circe

import kinesis.mock.instances.circe.*
import kinesis.mock.models.*

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_SubscribeToShard.html
final case class SubscribeToShardRequest(
    consumerArn: ConsumerArn,
    shardId: String,
    startingPosition: StartingPosition
)

object SubscribeToShardRequest:
  def subscribeToShardRequestCirceEncoder(using
      EI: circe.Encoder[Instant]
  ): circe.Encoder[SubscribeToShardRequest] =
    given circe.Encoder[StartingPosition] =
      StartingPosition.startingPositionCirceEncoder
    circe.Encoder.forProduct3("ConsumerARN", "ShardId", "StartingPosition")(r =>
      (r.consumerArn, r.shardId, r.startingPosition)
    )

  def subscribeToShardRequestCirceDecoder(using
      DI: circe.Decoder[Instant]
  ): circe.Decoder[SubscribeToShardRequest] =
    given circe.Decoder[StartingPosition] =
      StartingPosition.startingPositionCirceDecoder
    c =>
      for
        consumerArn <- c.downField("ConsumerARN").as[ConsumerArn]
        shardId <- c.downField("ShardId").as[String]
        startingPosition <- c.downField("StartingPosition").as[StartingPosition]
      yield SubscribeToShardRequest(consumerArn, shardId, startingPosition)

  given subscribeToShardRequestEncoder: Encoder[SubscribeToShardRequest] =
    Encoder.instance(
      subscribeToShardRequestCirceEncoder(using instantBigDecimalCirceEncoder),
      subscribeToShardRequestCirceEncoder(using instantLongCirceEncoder)
    )

  given subscribeToShardRequestDecoder: Decoder[SubscribeToShardRequest] =
    Decoder.instance(
      subscribeToShardRequestCirceDecoder(using instantBigDecimalCirceDecoder),
      subscribeToShardRequestCirceDecoder(using instantLongCirceDecoder)
    )

  given Eq[SubscribeToShardRequest] = (x, y) =>
    x.consumerArn === y.consumerArn &&
      x.shardId == y.shardId &&
      x.startingPosition === y.startingPosition
