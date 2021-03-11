package kinesis.mock.models

import java.time.Instant

import io.circe._

final case class SequenceNumber(value: String)

object SequenceNumber {
  // See https://github.com/mhart/kinesalite/blob/master/db/index.js#L177-L186
  def create(
      shardCreateTime: Instant,
      shardIndex: Int,
      byte1: Option[String],
      seqIndex: Option[Int],
      seqTime: Option[Instant]
  ): SequenceNumber =
    SequenceNumber(
      BigInt(
        "2" +
          ("00000000" + BigInt(shardCreateTime.getEpochSecond()).toString(16))
            .takeRight(9) +
          BigInt(shardIndex).toString(16).takeRight(1) +
          ("0000000000000000" + BigInt(seqIndex.getOrElse(0)).toString(16))
            .takeRight(16) +
          byte1.getOrElse("00") +
          "00000000" + BigInt(
            seqTime.getOrElse(shardCreateTime).getEpochSecond()
          )
            .toString(16)
            .takeRight(9) +
          ("0000000" + BigInt(shardIndex).toString(16)).takeRight(8) +
          "2",
        16
      ).toString(16)
    )

  implicit val sequenceNumberCirceEncoder: Encoder[SequenceNumber] =
    Encoder[String].contramap(_.value)
  implicit val sequenceNumberCirceDecoder: Decoder[SequenceNumber] =
    Decoder[String].map(SequenceNumber.apply)
}
