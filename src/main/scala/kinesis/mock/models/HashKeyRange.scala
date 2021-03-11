package kinesis.mock.models

import io.circe._

final case class HashKeyRange(endingHashKey: BigInt, startingHashKey: BigInt)

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
}
