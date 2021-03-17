package kinesis.mock.models

import cats.kernel.Eq
import io.circe._

final case class HashKeyRange(endingHashKey: BigInt, startingHashKey: BigInt) {
  def isAdjacent(other: HashKeyRange): Boolean =
    endingHashKey == other.startingHashKey + BigInt(1) ||
      startingHashKey == other.endingHashKey + BigInt(1)
}

object HashKeyRange {
  implicit val hashKeyRangeCirceEncoder: Encoder[HashKeyRange] =
    Encoder.forProduct2(
      "EndingHashKey",
      "StartingHashKey"
    )(x => (x.endingHashKey, x.startingHashKey))

  implicit val hashKeyRangeCirceDecoder: Decoder[HashKeyRange] = { x =>
    for {
      endingHashKey <- x.downField("EndingHashKey").as[BigInt]
      startingHashKey <- x.downField("StartingHashKey").as[BigInt]
    } yield HashKeyRange(endingHashKey, startingHashKey)
  }

  implicit val hashKeyRangeEq: Eq[HashKeyRange] = Eq.fromUniversalEquals
}
