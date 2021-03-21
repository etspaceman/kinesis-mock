package kinesis.mock.api

import cats.kernel.Eq
import io.circe._

import kinesis.mock.models.StreamName

final case class ListStreamsResponse(
    hasMoreStreams: Boolean,
    streamNames: List[StreamName]
)

object ListStreamsResponse {
  implicit val listStreamsResponseCirceEncoder: Encoder[ListStreamsResponse] =
    Encoder.forProduct2("HasMoreStreams", "StreamNames")(x =>
      (x.hasMoreStreams, x.streamNames)
    )

  implicit val listStreamsResponseCirceDecoder: Decoder[ListStreamsResponse] =
    x =>
      for {
        hasMoreStreams <- x.downField("HasMoreStreams").as[Boolean]
        streamNames <- x.downField("StreamNames").as[List[StreamName]]
      } yield ListStreamsResponse(hasMoreStreams, streamNames)

  implicit val listStreamsResponseEq: Eq[ListStreamsResponse] =
    Eq.fromUniversalEquals
}
