package kinesis.mock.models

import io.circe._

final case class SequenceNumberRange(
    endingSequenceNumber: Option[SequenceNumber],
    startingSequenceNumber: SequenceNumber
)

object SequenceNumberRange {
  implicit val sequenceNumberRangeCirceEncoder: Encoder[SequenceNumberRange] =
    Encoder.forProduct2("EndingSequenceNumber", "StartingSequenceNumber")(x =>
      (x.endingSequenceNumber, x.startingSequenceNumber)
    )

  implicit val sequenceNumberRangeCirceDecoder: Decoder[SequenceNumberRange] = {
    x =>
      for {
        endingSequenceNumber <- x
          .downField("EndingSequenceNumber")
          .as[Option[SequenceNumber]]
        startingSequenceNumber <- x
          .downField("StartingSequenceNumber")
          .as[SequenceNumber]
      } yield SequenceNumberRange(endingSequenceNumber, startingSequenceNumber)

  }
}
