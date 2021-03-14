package kinesis.mock
package api

import scala.util.Try

import java.time.Instant
import java.util.Base64

import cats.data.Validated._
import cats.data._
import cats.syntax.all._
import io.circe._
import javax.crypto.Cipher
import javax.crypto.spec.{IvParameterSpec, SecretKeySpec}
import javax.xml.bind.DatatypeConverter

import kinesis.mock.InvalidArgumentException
import kinesis.mock.models.SequenceNumber

final case class ShardIterator(value: String) {
  def parse: ValidatedNel[KinesisMockException, ShardIteratorParts] = {
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    val decoded = Base64.getDecoder().decode(value)
    if (decoded.length < 152 || decoded.length > 280)
      InvalidArgumentException("Invalid shard iterator").invalidNel
    else {
      val now = Instant.now()
      cipher.init(
        Cipher.DECRYPT_MODE,
        ShardIterator.iteratorPwdKey,
        ShardIterator.iteratorPwdIv
      )
      val decrypted = new String(cipher.doFinal(decoded.drop(8)), "UTF-8")
      val split = decrypted.split("/")
      if (split.length != 5)
        InvalidArgumentException("Invalid shard iterator").invalidNel
      else {
        val iteratorTimeMillis = split.head
        val streamName = split(1)
        val shardId = split(2)
        val sequenceNumber = SequenceNumber(split(3))

        (
          CommonValidations.validateStreamName(streamName),
          CommonValidations.validateShardId(shardId),
          CommonValidations.validateSequenceNumber(sequenceNumber),
          if (Try(iteratorTimeMillis.toLong).isFailure)
            InvalidArgumentException(
              "Invalid ShardIterator, the time argument is not numeric"
            ).invalidNel
          else Valid(()),
          if (
            Try(iteratorTimeMillis.toLong)
              .exists(x => x <= 0 || x > now.toEpochMilli())
          )
            InvalidArgumentException(
              "Invalid ShardIterator, the the time argument must be between 0 and now"
            ).invalidNel
          else Valid(()),
          if (now.toEpochMilli() - iteratorTimeMillis.toLong > 300000)
            InvalidArgumentException(
              "The shard iterator has expired. Shard iterators are only avlid for 300 seconds"
            ).invalidNel
          else Valid(())
        ).mapN((_, _, _, _, _, _) =>
          ShardIteratorParts(streamName, shardId, sequenceNumber)
        )
      }
    }
  }
}

final case class ShardIteratorParts(
    streamName: String,
    shardId: String,
    sequenceNumber: SequenceNumber
)

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
