package kinesis.mock
package api

import cats.Eq
import io.circe

import kinesis.mock.models.StreamName

final case class ListStreamsResponse(
    hasMoreStreams: Boolean,
    streamNames: List[StreamName]
)

object ListStreamsResponse {
  implicit val listStreamsResponseCirceEncoder
      : circe.Encoder[ListStreamsResponse] =
    circe.Encoder.forProduct2("HasMoreStreams", "StreamNames")(x =>
      (x.hasMoreStreams, x.streamNames)
    )

  implicit val listStreamsResponseCirceDecoder
      : circe.Decoder[ListStreamsResponse] =
    x =>
      for {
        hasMoreStreams <- x.downField("HasMoreStreams").as[Boolean]
        streamNames <- x.downField("StreamNames").as[List[StreamName]]
      } yield ListStreamsResponse(hasMoreStreams, streamNames)
  implicit val listStreamsResponseEncoder: Encoder[ListStreamsResponse] =
    Encoder.derive
  implicit val listStreamsResponseDecoder: Decoder[ListStreamsResponse] =
    Decoder.derive
  implicit val listStreamsResponseEq: Eq[ListStreamsResponse] =
    Eq.fromUniversalEquals
}
