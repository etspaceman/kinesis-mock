package kinesis.mock
package api

import cats.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models.Consumer

final case class DescribeStreamConsumerResponse(consumerDescription: Consumer)

object DescribeStreamConsumerResponse {
  def describeStreamConsumerResponseCirceEncoder(implicit
      EC: circe.Encoder[Consumer]
  ): circe.Encoder[DescribeStreamConsumerResponse] =
    circe.Encoder.forProduct1("ConsumerDescription")(_.consumerDescription)

  def describeStreamConsumerResponseCirceDecoder(implicit
      DC: circe.Decoder[Consumer]
  ): circe.Decoder[DescribeStreamConsumerResponse] = {
    _.downField("ConsumerDescription")
      .as[Consumer]
      .map(DescribeStreamConsumerResponse.apply)
  }
  implicit val describeStreamConsumerResponseEncoder
      : Encoder[DescribeStreamConsumerResponse] =
    Encoder.instance(
      describeStreamConsumerResponseCirceEncoder(
        Encoder[Consumer].circeEncoder
      ),
      describeStreamConsumerResponseCirceEncoder(
        Encoder[Consumer].circeCborEncoder
      )
    )
  implicit val describeStreamConsumerResponseDecoder
      : Decoder[DescribeStreamConsumerResponse] =
    Decoder.instance(
      describeStreamConsumerResponseCirceDecoder(
        Decoder[Consumer].circeDecoder
      ),
      describeStreamConsumerResponseCirceDecoder(
        Decoder[Consumer].circeCborDecoder
      )
    )
  implicit val describeStreamConsumerResponseEq
      : Eq[DescribeStreamConsumerResponse] = (x, y) =>
    x.consumerDescription === y.consumerDescription
}
