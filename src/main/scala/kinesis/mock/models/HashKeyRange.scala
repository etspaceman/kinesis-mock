package kinesis.mock
package models

import cats.kernel.Eq
import io.circe
import io.circe.syntax._

final case class HashKeyRange(endingHashKey: BigInt, startingHashKey: BigInt) {
  def isAdjacent(other: HashKeyRange): Boolean =
    endingHashKey == other.startingHashKey + BigInt(1) ||
      startingHashKey == other.endingHashKey + BigInt(1)
}

object HashKeyRange {
  implicit val hashKeyRangeCirceEncoder: circe.Encoder[HashKeyRange] = x =>
    circe
      .JsonObject(
        "EndingHashKey" -> x.endingHashKey.toString.asJson,
        "StartingHashKey" -> x.startingHashKey.toString.asJson
      )
      .asJson

  implicit val hashKeyRangeCirceDecoder: circe.Decoder[HashKeyRange] = { x =>
    for {
      endingHashKey <- x.downField("EndingHashKey").as[String].map(BigInt.apply)
      startingHashKey <- x
        .downField("StartingHashKey")
        .as[String]
        .map(BigInt.apply)
    } yield HashKeyRange(endingHashKey, startingHashKey)
  }

  implicit val hashKeyRangeEncoder: Encoder[HashKeyRange] =
    Encoder.derive
  implicit val hashKeyRangeDecoder: Decoder[HashKeyRange] =
    Decoder.derive

  implicit val hashKeyRangeEq: Eq[HashKeyRange] = Eq.fromUniversalEquals
}
