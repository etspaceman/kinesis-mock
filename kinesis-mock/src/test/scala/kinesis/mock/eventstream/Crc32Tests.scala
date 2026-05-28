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

class Crc32Tests extends munit.FunSuite:
  test("crc32 of empty input is 0") {
    assertEquals(Crc32.compute(ByteVector.empty), 0L)
  }
  test("crc32 of '123456789' is 0xcbf43926") {
    assertEquals(
      Crc32.compute(ByteVector("123456789".getBytes("US-ASCII"))),
      0xcbf43926L
    )
  }
  test("crc32 matches known vector for arbitrary bytes") {
    val bytes = ByteVector(0xde, 0xad, 0xbe, 0xef)
    assertEquals(Crc32.compute(bytes), 0x7c9ca35aL)
  }
