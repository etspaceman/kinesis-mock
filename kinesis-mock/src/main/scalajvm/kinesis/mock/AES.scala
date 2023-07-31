package kinesis.mock

import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

object AES {
  def encrypt(
      str: String,
      iteratorPwdKey: Array[Byte],
      iteratorPwdIv: Array[Byte]
  ): Array[Byte] = {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(
      Cipher.ENCRYPT_MODE,
      new SecretKeySpec(iteratorPwdKey, "AES"),
      new IvParameterSpec(iteratorPwdIv)
    )

    cipher.doFinal(str.getBytes("UTF-8"))
  }

  def decrypt(
      bytes: Array[Byte],
      iteratorPwdKey: Array[Byte],
      iteratorPwdIv: Array[Byte]
  ): Array[Byte] = {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(
      Cipher.DECRYPT_MODE,
      new SecretKeySpec(iteratorPwdKey, "AES"),
      new IvParameterSpec(iteratorPwdIv)
    )
    cipher.doFinal(bytes)
  }
}
