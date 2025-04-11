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
import io.circe

import kinesis.mock.models.ShardIterator

final case class GetShardIteratorResponse(shardIterator: ShardIterator)

object GetShardIteratorResponse:
  given getShardIteratorResponseCirceEncoder
      : circe.Encoder[GetShardIteratorResponse] =
    circe.Encoder.forProduct1("ShardIterator")(_.shardIterator)
  given getShardIteratorResponseCirceDecoder
      : circe.Decoder[GetShardIteratorResponse] =
    _.downField("ShardIterator")
      .as[ShardIterator]
      .map(GetShardIteratorResponse.apply)
  given getShardIteratorResponseEncoder: Encoder[GetShardIteratorResponse] =
    Encoder.derive
  given getShardIteratorResponseDecoder: Decoder[GetShardIteratorResponse] =
    Decoder.derive
  given getShardIteratorResponseEq: Eq[GetShardIteratorResponse] =
    Eq.fromUniversalEquals
