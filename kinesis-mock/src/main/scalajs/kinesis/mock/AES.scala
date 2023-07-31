package kinesis.mock

import scala.scalajs.js
import scala.scalajs.js.annotation.JSImport
import scala.scalajs.js.typedarray._

import scodec.bits.ByteVector

object AES {
  def encrypt(
      str: String,
      iteratorPwdKey: Array[Byte],
      iteratorPwdIv: Array[Byte]
  ): Array[Byte] = {
    val cipher = createCipheriv(
      "aes-256-cbc",
      ByteVector(iteratorPwdKey).toUint8Array,
      ByteVector(iteratorPwdIv).toUint8Array
    )

    (ByteVector.fromUint8Array(cipher.update(str, "utf-8")) ++
      ByteVector.fromUint8Array(cipher.`final`())).toArray
  }

  def decrypt(
      bytes: Array[Byte],
      iteratorPwdKey: Array[Byte],
      iteratorPwdIv: Array[Byte]
  ): Array[Byte] = {
    val cipher = createDecipheriv(
      "aes-256-cbc",
      ByteVector(iteratorPwdKey).toUint8Array,
      ByteVector(iteratorPwdIv).toUint8Array
    )

    (ByteVector
      .fromUint8Array(cipher.update(ByteVector(bytes).toUint8Array)) ++
      ByteVector.fromUint8Array(cipher.`final`())).toArray
  }

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
  private[mock] trait Cipher extends js.Object {
    def `final`(): Uint8Array = js.native
    def update(data: String, inputEncoding: String): Uint8Array = js.native
  }

  @js.native
  private[mock] trait Decipher extends js.Object {
    def `final`(): Uint8Array = js.native
    def update(data: Uint8Array): Uint8Array = js.native
  }
}
