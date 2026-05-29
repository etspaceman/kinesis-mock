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
      @annotation.tailrec
      def step(c: Int, j: Int): Int =
        if j >= 8 then c
        else
          val next =
            if (c & 1) != 0 then 0xedb88320 ^ (c >>> 1) else c >>> 1
          step(next, j + 1)
      step(i, 0)
    }

  def compute(bytes: ByteVector): Long =
    val arr = bytes.toArray
    @annotation.tailrec
    def step(c: Int, i: Int): Int =
      if i >= arr.length then c
      else step(table((c ^ arr(i)) & 0xff) ^ (c >>> 8), i + 1)
    (step(0xffffffff, 0) ^ 0xffffffff).toLong & 0xffffffffL
