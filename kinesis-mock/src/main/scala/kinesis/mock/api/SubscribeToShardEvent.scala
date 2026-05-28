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

import scala.collection.immutable.Queue

import cats.Eq
import cats.syntax.all.*
import io.circe

import kinesis.mock.models.*

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_SubscribeToShardEvent.html
final case class SubscribeToShardEvent(
    records: Queue[KinesisRecord],
    continuationSequenceNumber: SequenceNumber,
    millisBehindLatest: Long,
    childShards: Option[Vector[ChildShard]]
)

object SubscribeToShardEvent:
  def subscribeToShardEventCirceEncoder(using
      EKR: circe.Encoder[KinesisRecord]
  ): circe.Encoder[SubscribeToShardEvent] =
    circe.Encoder.forProduct4(
      "Records",
      "ContinuationSequenceNumber",
      "MillisBehindLatest",
      "ChildShards"
    )(e =>
      (
        e.records,
        e.continuationSequenceNumber,
        e.millisBehindLatest,
        e.childShards
      )
    )

  def subscribeToShardEventCirceDecoder(using
      DKR: circe.Decoder[KinesisRecord]
  ): circe.Decoder[SubscribeToShardEvent] =
    c =>
      for
        records <- c.downField("Records").as[Queue[KinesisRecord]]
        continuationSequenceNumber <- c
          .downField("ContinuationSequenceNumber")
          .as[SequenceNumber]
        millisBehindLatest <- c.downField("MillisBehindLatest").as[Long]
        childShards <- c.downField("ChildShards").as[Option[Vector[ChildShard]]]
      yield SubscribeToShardEvent(
        records,
        continuationSequenceNumber,
        millisBehindLatest,
        childShards
      )

  given subscribeToShardEventEncoder: Encoder[SubscribeToShardEvent] =
    Encoder.instance(
      subscribeToShardEventCirceEncoder(using
        Encoder[KinesisRecord].circeEncoder
      ),
      subscribeToShardEventCirceEncoder(using
        Encoder[KinesisRecord].circeCborEncoder
      )
    )

  given subscribeToShardEventDecoder: Decoder[SubscribeToShardEvent] =
    Decoder.instance(
      subscribeToShardEventCirceDecoder(using
        Decoder[KinesisRecord].circeDecoder
      ),
      subscribeToShardEventCirceDecoder(using
        Decoder[KinesisRecord].circeCborDecoder
      )
    )

  given Eq[SubscribeToShardEvent] = (x, y) =>
    x.records === y.records &&
      x.continuationSequenceNumber == y.continuationSequenceNumber &&
      x.millisBehindLatest == y.millisBehindLatest &&
      x.childShards == y.childShards
