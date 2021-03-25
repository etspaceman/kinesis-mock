package kinesis.mock.api

import cats.kernel.Eq
import io.circe._

import kinesis.mock.models.StreamDescriptionSummary

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
  implicit val describeStreamSummaryResponseEq
      : Eq[DescribeStreamSummaryResponse] = Eq.fromUniversalEquals
}
