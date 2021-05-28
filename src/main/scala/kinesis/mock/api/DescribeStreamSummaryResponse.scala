package kinesis.mock
package api

import cats.kernel.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models.StreamDescriptionSummary

final case class DescribeStreamSummaryResponse(
    streamDescriptionSummary: StreamDescriptionSummary
)

object DescribeStreamSummaryResponse {
  def describeStreamSummaryResponseCirceEncoder(implicit
      ESDS: circe.Encoder[StreamDescriptionSummary]
  ): circe.Encoder[DescribeStreamSummaryResponse] =
    circe.Encoder.forProduct1("StreamDescriptionSummary")(
      _.streamDescriptionSummary
    )

  def describeStreamSummaryResponseCirceDecoder(implicit
      DSDS: circe.Decoder[StreamDescriptionSummary]
  ): circe.Decoder[DescribeStreamSummaryResponse] = {
    _.downField("StreamDescriptionSummary")
      .as[StreamDescriptionSummary]
      .map(DescribeStreamSummaryResponse.apply)
  }

  implicit val describeStreamSummaryResponseEncoder
      : Encoder[DescribeStreamSummaryResponse] = Encoder.instance(
    describeStreamSummaryResponseCirceEncoder(
      Encoder[StreamDescriptionSummary].circeEncoder
    ),
    describeStreamSummaryResponseCirceEncoder(
      Encoder[StreamDescriptionSummary].circeCborEncoder
    )
  )
  implicit val describeStreamSummaryResponseDecoder
      : Decoder[DescribeStreamSummaryResponse] = Decoder.instance(
    describeStreamSummaryResponseCirceDecoder(
      Decoder[StreamDescriptionSummary].circeDecoder
    ),
    describeStreamSummaryResponseCirceDecoder(
      Decoder[StreamDescriptionSummary].circeCborDecoder
    )
  )
  implicit val describeStreamSummaryResponseEq
      : Eq[DescribeStreamSummaryResponse] = (x, y) =>
    x.streamDescriptionSummary === y.streamDescriptionSummary
}
