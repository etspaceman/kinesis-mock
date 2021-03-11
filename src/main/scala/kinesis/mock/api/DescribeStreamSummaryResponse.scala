package kinesis.mock.api

import io.circe._

final case class DescribeStreamSummaryResponse(
    streamDescriptionSummary: StreamDescriptionSummary
)

object DescribeStreamSummaryResponse {
  implicit val describeStreamSummaryResponseCirceEncoder
      : Encoder[DescribeStreamSummaryResponse] =
    Encoder.forProduct1("StreamDescriptionSummary")(_.streamDescriptionSummary)

  implicit val describeStreamSummaryResponseCirceDecoder
      : Decoder[DescribeStreamSummaryResponse] = {
    _.downField("StreamDescriptionSummary")
      .as[StreamDescriptionSummary]
      .map(DescribeStreamSummaryResponse.apply)
  }
}
