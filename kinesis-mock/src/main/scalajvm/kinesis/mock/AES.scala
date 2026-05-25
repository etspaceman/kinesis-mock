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

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AES:
  def encrypt(
      str: String,
      iteratorPwdKey: Array[Byte],
      iteratorPwdIv: Array[Byte]
  ): Array[Byte] =
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(
      Cipher.ENCRYPT_MODE,
      new SecretKeySpec(iteratorPwdKey, "AES"),
      new IvParameterSpec(iteratorPwdIv)
    )

    cipher.doFinal(str.getBytes("UTF-8"))

  def decrypt(
      bytes: Array[Byte],
      iteratorPwdKey: Array[Byte],
      iteratorPwdIv: Array[Byte]
  ): Array[Byte] =
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(
      Cipher.DECRYPT_MODE,
      new SecretKeySpec(iteratorPwdKey, "AES"),
      new IvParameterSpec(iteratorPwdIv)
    )
    cipher.doFinal(bytes)
