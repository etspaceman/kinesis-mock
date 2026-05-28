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

/** IEEE CRC32, polynomial 0xedb88320, init/xor 0xffffffff. Cross-platform
  * pure-Scala implementation so it works on Scala.js as well as the JVM.
  */
object Crc32:
  private val table: Array[Int] =
    Array.tabulate(256) { i =>
      var c = i
      var j = 0
      while j < 8 do
        c = if (c & 1) != 0 then 0xedb88320 ^ (c >>> 1) else c >>> 1
        j += 1
      c
    }

  def compute(bytes: ByteVector): Long =
    var c = 0xffffffff
    val arr = bytes.toArray
    var i = 0
    while i < arr.length do
      c = table((c ^ arr(i)) & 0xff) ^ (c >>> 8)
      i += 1
    (c ^ 0xffffffff).toLong & 0xffffffffL
