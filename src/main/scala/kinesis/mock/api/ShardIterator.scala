package kinesis.mock.api

import java.time.Instant
import java.util.Base64

import io.circe._
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import javax.xml.bind.DatatypeConverter

import kinesis.mock.models.SequenceNumber

final case class ShardIterator(value: String)

object ShardIterator {

  private val iteratorPwdKey = new SecretKeySpec(
    DatatypeConverter.parseHexBinary(
      "1133a5a833666b49abf28c8ba302930f0b2fb240dccd43cf4dfbc0ca91f17751"
    ),
    "AES"
  )
  private val iteratorPwdIv = new IvParameterSpec(
    DatatypeConverter.parseHexBinary("7bf139dbabbea2d9995d6fcae1dff7da")
  )
  
  // See https://github.com/mhart/kinesalite/blob/master/db/index.js#L252
  def create(
      streamName: String,
      shardId: String,
      sequenceNumber: SequenceNumber
  ): ShardIterator = {
    val encryptString =
      (List.fill(14)("0").mkString + Instant.now().toEpochMilli())
        .takeRight(14) +
        s"/$streamName" +
        s"/$shardId" +
        s"/${sequenceNumber.value}" +
        List.fill(37)("0").mkString

    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, iteratorPwdKey, iteratorPwdIv)

    val encryptedBytes = Array[Byte](0, 0, 0, 0, 0, 0, 0, 1) ++
      cipher.doFinal(encryptString.getBytes("UTF-8"))
    ShardIterator(Base64.getEncoder().encodeToString(encryptedBytes))
  }

  implicit val shardIteratorCirceEncoder: Encoder[ShardIterator] =
    Encoder[String].contramap(_.value)

  implicit val shardIteratorCirceDecoder: Decoder[ShardIterator] =
    Decoder[String].map(ShardIterator.apply)
}
