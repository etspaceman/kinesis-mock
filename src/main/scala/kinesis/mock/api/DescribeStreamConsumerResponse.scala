package kinesis.mock.api

import io.circe._

import kinesis.mock.models.Consumer

final case class DescribeStreamConsumerResponse(consumerDescription: Consumer)

object DescribeStreamConsumerResponse {
  implicit val describeStreamConsumerResponseCirceEncoder
      : Encoder[DescribeStreamConsumerResponse] =
    Encoder.forProduct1("ConsumerDescription")(_.consumerDescription)

  implicit val describeStreamConsumerResponseCirceDecoder
      : Decoder[DescribeStreamConsumerResponse] = {
    _.downField("ConsumerDescription")
      .as[Consumer]
      .map(DescribeStreamConsumerResponse.apply)
  }
}
