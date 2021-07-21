package kinesis.mock
package api

import cats.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._

final case class RegisterStreamConsumerResponse(consumer: ConsumerSummary)

object RegisterStreamConsumerResponse {
  def registerStreamConsumerResponseCirceEncoder(implicit
      EC: circe.Encoder[ConsumerSummary]
  ): circe.Encoder[RegisterStreamConsumerResponse] =
    circe.Encoder.forProduct1("Consumer")(_.consumer)
  def registerStreamConsumerResponseCirceDecoder(implicit
      DC: circe.Decoder[ConsumerSummary]
  ): circe.Decoder[RegisterStreamConsumerResponse] = _.downField("Consumer")
    .as[ConsumerSummary]
    .map(RegisterStreamConsumerResponse.apply)
  implicit val registerStreamConsumerResponseEncoder
      : Encoder[RegisterStreamConsumerResponse] = Encoder.instance(
    registerStreamConsumerResponseCirceEncoder(
      Encoder[ConsumerSummary].circeEncoder
    ),
    registerStreamConsumerResponseCirceEncoder(
      Encoder[ConsumerSummary].circeCborEncoder
    )
  )
  implicit val registerStreamConsumerResponseDecoder
      : Decoder[RegisterStreamConsumerResponse] = Decoder.instance(
    registerStreamConsumerResponseCirceDecoder(
      Decoder[ConsumerSummary].circeDecoder
    ),
    registerStreamConsumerResponseCirceDecoder(
      Decoder[ConsumerSummary].circeCborDecoder
    )
  )
  implicit val registerStreamConsumerResponseEq
      : Eq[RegisterStreamConsumerResponse] = (x, y) => x.consumer === y.consumer
}
