package kinesis.mock

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray._

object AES {
  def encrypt(
      str: String,
      iteratorPwdKey: Array[Byte],
      iteratorPwdIv: Array[Byte]
  ): Array[Byte] = {
    val cipher = createCipheriv(
      "aes-256-cbc",
      Int8Array.from(iteratorPwdKey.toTypedArray),
      Int8Array.from(iteratorPwdIv.toTypedArray)
    )

    val res = new Int8Array(cipher.update(str, "utf-8")).toArray
    cipher.`final`()
    res
  }

  def decrypt(
      bytes: Array[Byte],
      iteratorPwdKey: Array[Byte],
      iteratorPwdIv: Array[Byte]
  ): Array[Byte] = {
    val cipher = createDecipheriv(
      "aes-256-cbc",
      Int8Array.from(iteratorPwdKey.toTypedArray),
      Int8Array.from(iteratorPwdIv.toTypedArray)
    )

    val res = new Int8Array(cipher.update(bytes.toTypedArray)).toArray
    cipher.`final`()
    res
  }

  @js.native
  @JSImport("crypto", "createCipheriv")
  private[mock] def createCipheriv(
      algorithm: String,
      key: Int8Array,
      iv: Int8Array
  ): Cipher = js.native

  @js.native
  @JSImport("crypto", "createDecipheriv")
  private[mock] def createDecipheriv(
      algorithm: String,
      key: Int8Array,
      iv: Int8Array
  ): Decipher = js.native

  @js.native
  private[mock] trait Cipher extends js.Object {
    def `final`(): ArrayBuffer = js.native
    def update(data: String, inputEncoding: String): ArrayBuffer = js.native
  }

  @js.native
  private[mock] trait Decipher extends js.Object {
    def `final`(): ArrayBuffer = js.native
    def update(data: Int8Array): ArrayBuffer = js.native
  }
}
