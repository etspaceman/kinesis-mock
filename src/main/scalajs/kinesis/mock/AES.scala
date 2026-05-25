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

package kinesis.mock

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray.*

import scodec.bits.ByteVector

object AES:
  def encrypt(
      str: String,
      iteratorPwdKey: Array[Byte],
      iteratorPwdIv: Array[Byte]
  ): Array[Byte] =
    val cipher = createCipheriv(
      "aes-256-cbc",
      ByteVector(iteratorPwdKey).toUint8Array,
      ByteVector(iteratorPwdIv).toUint8Array
    )

    (ByteVector.fromUint8Array(cipher.update(str, "utf-8")) ++
      ByteVector.fromUint8Array(cipher.`final`())).toArray

  def decrypt(
      bytes: Array[Byte],
      iteratorPwdKey: Array[Byte],
      iteratorPwdIv: Array[Byte]
  ): Array[Byte] =
    val cipher = createDecipheriv(
      "aes-256-cbc",
      ByteVector(iteratorPwdKey).toUint8Array,
      ByteVector(iteratorPwdIv).toUint8Array
    )

    (ByteVector
      .fromUint8Array(cipher.update(ByteVector(bytes).toUint8Array)) ++
      ByteVector.fromUint8Array(cipher.`final`())).toArray

  @js.native
  @JSImport("crypto", "createCipheriv")
  private[mock] def createCipheriv(
      algorithm: String,
      key: Uint8Array,
      iv: Uint8Array
  ): Cipher = js.native

  @js.native
  @JSImport("crypto", "createDecipheriv")
  private[mock] def createDecipheriv(
      algorithm: String,
      key: Uint8Array,
      iv: Uint8Array
  ): Decipher = js.native

  @js.native
  private[mock] trait Cipher extends js.Object:
    def `final`(): Uint8Array = js.native
    def update(data: String, inputEncoding: String): Uint8Array = js.native

  @js.native
  private[mock] trait Decipher extends js.Object:
    def `final`(): Uint8Array = js.native
    def update(data: Uint8Array): Uint8Array = js.native
