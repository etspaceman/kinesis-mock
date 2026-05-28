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
