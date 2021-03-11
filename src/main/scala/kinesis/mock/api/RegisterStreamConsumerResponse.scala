package kinesis.mock.api

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
}
