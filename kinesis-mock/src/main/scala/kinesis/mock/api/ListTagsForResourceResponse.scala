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

import cats.Eq
import io.circe

import kinesis.mock.models.TagList

final case class ListTagsForResourceResponse(
    tags: TagList,
    nextToken: Option[String]
)

object ListTagsForResourceResponse:
  given listTagsForResourceResponseCirceEncoder
      : circe.Encoder[ListTagsForResourceResponse] =
    circe.Encoder.forProduct2("Tags", "NextToken")(x => (x.tags, x.nextToken))

  given listTagsForResourceResponseCirceDecoder
      : circe.Decoder[ListTagsForResourceResponse] = x =>
    for
      tags <- x.downField("Tags").as[TagList]
      nextToken <- x.downField("NextToken").as[Option[String]]
    yield ListTagsForResourceResponse(tags, nextToken)

  given listTagsForResourceResponseEncoder
      : Encoder[ListTagsForResourceResponse] = Encoder.derive
  given listTagsForResourceResponseDecoder
      : Decoder[ListTagsForResourceResponse] = Decoder.derive
  given Eq[ListTagsForResourceResponse] = Eq.fromUniversalEquals
