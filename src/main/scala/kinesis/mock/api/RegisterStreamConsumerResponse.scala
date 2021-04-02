package kinesis.mock.api

import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

final case class RegisterStreamConsumerResponse(consumer: Consumer)

object RegisterStreamConsumerResponse {
  implicit val registerStreamConsumerResponseCirceEncoder
      : Encoder[RegisterStreamConsumerResponse] =
    Encoder.forProduct1("Consumer")(_.consumer)
  implicit val registerStreamConsumerResponseCirceDecoder
      : Decoder[RegisterStreamConsumerResponse] = _.downField("Consumer")
    .as[Consumer]
    .map(RegisterStreamConsumerResponse.apply)
  implicit val registerStreamConsumerResponseEq
      : Eq[RegisterStreamConsumerResponse] = (x, y) => x.consumer === y.consumer
}
