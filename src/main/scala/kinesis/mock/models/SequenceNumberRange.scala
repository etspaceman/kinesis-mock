package kinesis.mock
package models

import cats.Eq
import io.circe

final case class SequenceNumberRange(
    endingSequenceNumber: Option[SequenceNumber],
    startingSequenceNumber: SequenceNumber
)

object SequenceNumberRange {
  implicit val sequenceNumberRangeCirceEncoder
      : circe.Encoder[SequenceNumberRange] =
    circe.Encoder.forProduct2("EndingSequenceNumber", "StartingSequenceNumber")(
      x => (x.endingSequenceNumber, x.startingSequenceNumber)
    )

  implicit val sequenceNumberRangeCirceDecoder
      : circe.Decoder[SequenceNumberRange] = { x =>
    for {
      endingSequenceNumber <- x
        .downField("EndingSequenceNumber")
        .as[Option[SequenceNumber]]
      startingSequenceNumber <- x
        .downField("StartingSequenceNumber")
        .as[SequenceNumber]
    } yield SequenceNumberRange(endingSequenceNumber, startingSequenceNumber)
  }

  implicit val sequenceNumberRangeEncoder: Encoder[SequenceNumberRange] =
    Encoder.derive

  implicit val sequenceNumberRangeDecoder: Decoder[SequenceNumberRange] =
    Decoder.derive

  implicit val sequenceNumberRangeEq: Eq[SequenceNumberRange] =
    Eq.fromUniversalEquals
}
