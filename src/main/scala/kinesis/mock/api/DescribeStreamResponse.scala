package kinesis.mock.api

import cats.kernel.Eq
import io.circe._

final case class DescribeStreamResponse(streamDescription: StreamDescription)

object DescribeStreamResponse {
  implicit val describeStreamResponseCirceEncoder
      : Encoder[DescribeStreamResponse] =
    Encoder.forProduct1("StreamDescription")(_.streamDescription)

  implicit val describeStreamResponseCirceDecoder
      : Decoder[DescribeStreamResponse] = {
    _.downField("StreamDescription")
      .as[StreamDescription]
      .map(DescribeStreamResponse.apply)
  }
  implicit val describeStreamResponseEq: Eq[DescribeStreamResponse] =
    Eq.fromUniversalEquals
}
