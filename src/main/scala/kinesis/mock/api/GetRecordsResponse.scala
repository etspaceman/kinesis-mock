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
package api

import scala.collection.immutable.Queue

import cats.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._

final case class GetRecordsResponse(
    childShards: Option[Vector[ChildShard]],
    millisBehindLatest: Long,
    nextShardIterator: Option[ShardIterator],
    records: Queue[KinesisRecord]
)

object GetRecordsResponse {
  def getRecordsResponseCirceEncoder(implicit
      EKR: circe.Encoder[KinesisRecord]
  ): circe.Encoder[GetRecordsResponse] =
    circe.Encoder.forProduct4(
      "ChildShards",
      "MillisBehindLatest",
      "NextShardIterator",
      "Records"
    )(x =>
      (x.childShards, x.millisBehindLatest, x.nextShardIterator, x.records)
    )

  def getRecordsResponseCirceDecoder(implicit
      DKR: circe.Decoder[KinesisRecord]
  ): circe.Decoder[GetRecordsResponse] =
    x =>
      for {
        childShards <- x.downField("ChildShards").as[Option[Vector[ChildShard]]]
        millisBehindLatest <- x.downField("MillisBehindLatest").as[Long]
        nextShardIterator <- x
          .downField("NextShardIterator")
          .as[Option[ShardIterator]]
        records <- x.downField("Records").as[Queue[KinesisRecord]]
      } yield GetRecordsResponse(
        childShards,
        millisBehindLatest,
        nextShardIterator,
        records
      )

  implicit val getRecordsResponseEncoder: Encoder[GetRecordsResponse] =
    Encoder.instance(
      getRecordsResponseCirceEncoder(Encoder[KinesisRecord].circeEncoder),
      getRecordsResponseCirceEncoder(Encoder[KinesisRecord].circeCborEncoder)
    )

  implicit val getRecordsResponseDecoder: Decoder[GetRecordsResponse] =
    Decoder.instance(
      getRecordsResponseCirceDecoder(Decoder[KinesisRecord].circeDecoder),
      getRecordsResponseCirceDecoder(Decoder[KinesisRecord].circeCborDecoder)
    )

  implicit val getRecordsResponseEq: Eq[GetRecordsResponse] = (x, y) =>
    x.childShards == y.childShards &&
      x.millisBehindLatest == y.millisBehindLatest &&
      x.nextShardIterator == y.nextShardIterator &&
      x.records === y.records
}
