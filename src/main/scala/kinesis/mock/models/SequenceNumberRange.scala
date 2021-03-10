package kinesis.mock.models

import java.time.Instant

final case class SequenceNumberRange(
    startingSequenceNumber: String,
    endingSequenceNumber: Option[String]
)

object SequenceNumberRange {
  // See https://github.com/mhart/kinesalite/blob/master/db/index.js#L177-L186
  def stringifySequence(
      shardCreateTime: Instant,
      shardIndex: Int,
      byte1: Option[String],
      seqIndex: Option[Int],
      seqTime: Option[Instant]
  ): String =
    BigInt(
      "2" +
        ("00000000" + BigInt(shardCreateTime.getEpochSecond()).toString(16))
          .takeRight(9) +
        BigInt(shardIndex).toString(16).takeRight(1) +
        ("0000000000000000" + BigInt(seqIndex.getOrElse(0)).toString(16))
          .takeRight(16) +
        byte1.getOrElse("00") +
        "00000000" + BigInt(seqTime.getOrElse(shardCreateTime).getEpochSecond())
          .toString(16)
          .takeRight(9) +
        ("0000000" + BigInt(shardIndex).toString(16)).takeRight(8) +
        "2",
      16
    ).toString(16)
}
