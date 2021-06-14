package kinesis.mock
package api

import cats.kernel.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models.StreamDescription

final case class DescribeStreamResponse(streamDescription: StreamDescription)

object DescribeStreamResponse {
  def describeStreamResponseCirceEncoder(implicit
      ESD: circe.Encoder[StreamDescription]
  ): circe.Encoder[DescribeStreamResponse] =
    circe.Encoder.forProduct1("StreamDescription")(_.streamDescription)

  def describeStreamResponseCirceDecoder(implicit
      DSD: circe.Decoder[StreamDescription]
  ): circe.Decoder[DescribeStreamResponse] = {
    _.downField("StreamDescription")
      .as[StreamDescription]
      .map(DescribeStreamResponse.apply)
  }
  implicit val describeStreamResponseEncoder: Encoder[DescribeStreamResponse] =
    Encoder.instance(
      describeStreamResponseCirceEncoder(
        Encoder[StreamDescription].circeEncoder
      ),
      describeStreamResponseCirceEncoder(
        Encoder[StreamDescription].circeCborEncoder
      )
    )
  implicit val describeStreamResponseDecoder: Decoder[DescribeStreamResponse] =
    Decoder.instance(
      describeStreamResponseCirceDecoder(
        Decoder[StreamDescription].circeDecoder
      ),
      describeStreamResponseCirceDecoder(
        Decoder[StreamDescription].circeCborDecoder
      )
    )
  implicit val describeStreamResponseEq: Eq[DescribeStreamResponse] =
    (x, y) => x.streamDescription === y.streamDescription
}
