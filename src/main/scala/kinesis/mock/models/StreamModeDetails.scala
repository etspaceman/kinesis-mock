package kinesis.mock
package models

import cats.Eq
import io.circe

final case class StreamModeDetails(
    streamMode: StreamMode
)

object StreamModeDetails {
  implicit val streamModeDetailsCirceEncoder: circe.Encoder[StreamModeDetails] =
    circe.Encoder.forProduct1(
      "StreamMode"
    )(x => x.streamMode)

  implicit val streamModeDetailsCirceDecoder: circe.Decoder[StreamModeDetails] =
    x =>
      for {
        streamMode <- x.downField("StreamMode").as[StreamMode]
      } yield StreamModeDetails(streamMode)

  implicit val streamModeDetailsEncoder: Encoder[StreamModeDetails] =
    Encoder.derive
  implicit val streamModeDetailsDecoder: Decoder[StreamModeDetails] =
    Decoder.derive

  implicit val streamModeDetailsEq: Eq[StreamModeDetails] =
    Eq.fromUniversalEquals
}
