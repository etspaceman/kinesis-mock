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

package kinesis.mock.models

import cats.Eq
import io.circe._

final case class TagList(tags: Vector[TagListEntry])

object TagList {
  given tagListEq: Eq[TagList] = Eq.fromUniversalEquals
  given tagListCirceEncoder: Encoder[TagList] =
    Encoder.encodeVector[TagListEntry].contramap(_.tags)
  given tagListCirceDecoder: Decoder[TagList] =
    Decoder[Vector[TagListEntry]].map(TagList.apply)
  def fromTags(tags: Tags): TagList = TagList(
    tags.tags.toVector.map { case (key, value) => TagListEntry(key, value) }
  )
}
