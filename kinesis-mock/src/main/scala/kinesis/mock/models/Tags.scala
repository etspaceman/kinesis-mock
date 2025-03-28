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

import scala.collection.SortedMap

import cats.{Eq, Monoid}
import io.circe._

final case class Tags(tags: SortedMap[String, String]) {
  def size: Int = tags.size
  def --(keys: IterableOnce[String]): Tags = copy(tags = tags.filterNot {
    case (key, _) => keys.iterator.contains(key)
  })
  override def toString: String = tags.toString()
  def toVector: Vector[(String, String)] = tags.toVector
}

object Tags {
  def empty: Tags = Tags(SortedMap.empty)
  def fromTagList(tagList: TagList): Tags = Tags(
    SortedMap.from(tagList.tags.map(x => (x.key, x.value)))
  )
  given tagsCirceEncoder: Encoder[Tags] =
    Encoder[SortedMap[String, String]].contramap(_.tags)
  given tagsCirceDecoder: Decoder[Tags] =
    Decoder[SortedMap[String, String]].map(Tags.apply)
  given tagsEq: Eq[Tags] = Eq.fromUniversalEquals
  given tagsMonoid: Monoid[Tags] = new Monoid[Tags] {
    override def combine(x: Tags, y: Tags): Tags = Tags(x.tags ++ y.tags)

    override def empty: Tags = Tags.empty

  }
}
