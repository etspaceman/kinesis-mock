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

import kinesis.mock.models.TagList

final case class ListTagsForStreamResponse(
    hasMoreTags: Boolean,
    tags: TagList
)

object ListTagsForStreamResponse {
  given listTagsForStreamResponseCirceEncoder
      : circe.Encoder[ListTagsForStreamResponse] =
    circe.Encoder.forProduct2("HasMoreTags", "Tags")(x =>
      (x.hasMoreTags, x.tags)
    )

  given listTagsForStreamResponseCirceDecoder
      : circe.Decoder[ListTagsForStreamResponse] =
    x =>
      for {
        hasMoreTags <- x.downField("HasMoreTags").as[Boolean]
        tags <- x.downField("Tags").as[TagList]
      } yield ListTagsForStreamResponse(hasMoreTags, tags)

  given listTagsForStreamResponseEncoder: Encoder[ListTagsForStreamResponse] =
    Encoder.derive
  given listTagsForStreamResponseDecoder: Decoder[ListTagsForStreamResponse] =
    Decoder.derive

  given listTagsForStreamResponseEq: Eq[ListTagsForStreamResponse] =
    Eq.fromUniversalEquals
}
