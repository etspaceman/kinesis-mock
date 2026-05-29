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

package kinesis.mock.eventstream

import scodec.bits.ByteVector

/** An AWS event-stream header. Wire format: name_len (u8) | name (UTF-8) |
  * value_type (u8) | value_len (u16 BE) | value (UTF-8)
  *
  * Only the String value type (7) is needed by SubscribeToShard.
  */
final case class EventStreamHeader(name: String, value: String)

object EventStreamHeader:
  private val StringType: Byte = 7

  def encode(h: EventStreamHeader): ByteVector =
    val nameBytes = ByteVector(h.name.getBytes("UTF-8"))
    val valueBytes = ByteVector(h.value.getBytes("UTF-8"))
    require(nameBytes.length <= 255, s"header name too long: ${h.name}")
    require(valueBytes.length <= 65535, s"header value too long")
    ByteVector(nameBytes.length.toByte) ++
      nameBytes ++
      ByteVector(StringType) ++
      ByteVector.fromShort(valueBytes.length.toShort) ++
      valueBytes

  def encodeAll(headers: List[EventStreamHeader]): ByteVector =
    headers.foldLeft(ByteVector.empty)(_ ++ encode(_))
