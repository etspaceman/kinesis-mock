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

import cats.Eq
import io.circe

final case class TagListEntry(key: String, value: String)

object TagListEntry {
  implicit val tagListEntryCirceEncoder: circe.Encoder[TagListEntry] =
    circe.Encoder.forProduct2("Key", "Value")(x => (x.key, x.value))
  implicit val tagListEntryCirceDecoder: circe.Decoder[TagListEntry] = x =>
    for {
      key <- x.downField("Key").as[String]
      value <- x.downField("Value").as[String]
    } yield TagListEntry(key, value)

  implicit val tagListEntryEncoder: Encoder[TagListEntry] = Encoder.derive
  implicit val tagListEntryDecoder: Decoder[TagListEntry] = Decoder.derive
  implicit val tagListEntryEq: Eq[TagListEntry] = Eq.fromUniversalEquals
}
