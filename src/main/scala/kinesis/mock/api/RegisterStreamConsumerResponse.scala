package kinesis.mock.api

import io.circe._

import kinesis.mock.models._
import cats.kernel.Eq

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
      : Eq[RegisterStreamConsumerResponse] = Eq.fromUniversalEquals
}
