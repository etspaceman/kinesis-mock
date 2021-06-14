package kinesis.mock
package api

import cats.kernel.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._

final case class RegisterStreamConsumerResponse(consumer: Consumer)

object RegisterStreamConsumerResponse {
  def registerStreamConsumerResponseCirceEncoder(implicit
      EC: circe.Encoder[Consumer]
  ): circe.Encoder[RegisterStreamConsumerResponse] =
    circe.Encoder.forProduct1("Consumer")(_.consumer)
  def registerStreamConsumerResponseCirceDecoder(implicit
      DC: circe.Decoder[Consumer]
  ): circe.Decoder[RegisterStreamConsumerResponse] = _.downField("Consumer")
    .as[Consumer]
    .map(RegisterStreamConsumerResponse.apply)
  implicit val registerStreamConsumerResponseEncoder
      : Encoder[RegisterStreamConsumerResponse] = Encoder.instance(
    registerStreamConsumerResponseCirceEncoder(Encoder[Consumer].circeEncoder),
    registerStreamConsumerResponseCirceEncoder(
      Encoder[Consumer].circeCborEncoder
    )
  )
  implicit val registerStreamConsumerResponseDecoder
      : Decoder[RegisterStreamConsumerResponse] = Decoder.instance(
    registerStreamConsumerResponseCirceDecoder(Decoder[Consumer].circeDecoder),
    registerStreamConsumerResponseCirceDecoder(
      Decoder[Consumer].circeCborDecoder
    )
  )
  implicit val registerStreamConsumerResponseEq
      : Eq[RegisterStreamConsumerResponse] = (x, y) => x.consumer === y.consumer
}
