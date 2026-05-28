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

import scodec.bits.*

class EventStreamCodecTests extends munit.FunSuite:
  test("encode single string header") {
    // ":message-type" (13 bytes) | type 7 | length 5 | "event" (5 bytes)
    val h = EventStreamHeader(":message-type", "event")
    val expected = hex"0d3a6d6573736167652d747970650700056576656e74"
    assertEquals(EventStreamHeader.encode(h), expected)
  }

  test("encode message: total length, headers length, prelude crc, message crc") {
    val msg = EventStreamMessage(
      headers = List(EventStreamHeader(":message-type", "event")),
      payload = ByteVector.empty
    )
    val encoded = EventStreamMessage.encode(msg)
    // headers encoded: 1 + 13 + 1 + 2 + 5 = 22 bytes
    // total = 12 prelude + 22 headers + 0 payload + 4 trailing crc = 38 (0x26)
    assertEquals(encoded.take(4), hex"00000026")
    assertEquals(encoded.slice(4, 8), hex"00000016") // 22
    // prelude CRC over first 8 bytes
    assertEquals(
      encoded.slice(8, 12),
      ByteVector.fromInt(Crc32.compute(encoded.take(8)).toInt)
    )
    // message CRC over everything except the trailing 4 bytes
    assertEquals(
      encoded.takeRight(4),
      ByteVector.fromInt(Crc32.compute(encoded.dropRight(4)).toInt)
    )
  }

  test("encode message: payload appears verbatim between headers and crc") {
    val payload = ByteVector("hello".getBytes("UTF-8"))
    val msg = EventStreamMessage(
      headers = List(EventStreamHeader(":content-type", "application/json")),
      payload = payload
    )
    val encoded = EventStreamMessage.encode(msg)
    assert(encoded.containsSlice(payload), "payload bytes missing from encoded frame")
  }
