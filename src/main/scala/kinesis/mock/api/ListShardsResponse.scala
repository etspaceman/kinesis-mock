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

import cats.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._

final case class ListShardsResponse(
    nextToken: Option[String],
    shards: Vector[ShardSummary]
)

object ListShardsResponse {
  implicit val listShardsResponseCirceEncoder
      : circe.Encoder[ListShardsResponse] =
    circe.Encoder.forProduct2("NextToken", "Shards")(x =>
      (x.nextToken, x.shards)
    )
  implicit val listShardsResponseCirceDecoder
      : circe.Decoder[ListShardsResponse] =
    x =>
      for {
        nextToken <- x.downField("NextToken").as[Option[String]]
        shards <- x.downField("Shards").as[Vector[ShardSummary]]
      } yield ListShardsResponse(nextToken, shards)
  implicit val listShardsResponseEncoder: Encoder[ListShardsResponse] =
    Encoder.derive
  implicit val listShardsResponseDecoder: Decoder[ListShardsResponse] =
    Decoder.derive
  implicit val listShardsResponseEq: Eq[ListShardsResponse] =
    (x, y) => x.nextToken == y.nextToken && x.shards === y.shards
}
