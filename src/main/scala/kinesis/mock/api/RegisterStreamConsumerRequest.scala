package kinesis.mock
package api

import cats.data.Validated._
import cats.data._
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_RegisterStreamConsumer.html
final case class RegisterStreamConsumerRequest(
    consumerName: ConsumerName,
    streamArn: String
) {
  def registerStreamConsumer(
      streams: Streams
  ): ValidatedNel[
    KinesisMockException,
    (Streams, RegisterStreamConsumerResponse)
  ] =
    CommonValidations
      .findStreamByArn(streamArn, streams)
      .andThen(stream =>
        (
          CommonValidations.validateStreamArn(streamArn),
          CommonValidations.validateConsumerName(consumerName),
          if (stream.consumers.size >= 20)
            LimitExceededException(
              s"Only 20 consumers can be registered to a stream at once"
            ).invalidNel
          else Valid(()),
          if (
            stream.consumers.values
              .filter(_.consumerStatus == ConsumerStatus.CREATING)
              .size >= 5
          )
            LimitExceededException(
              s"Only 5 consumers can be created at the same time"
            ).invalidNel
          else Valid(()),
          if (stream.consumers.get(consumerName).nonEmpty)
            ResourceInUseException(
              s"Consumer $consumerName exists"
            ).invalidNel
          else Valid(())
        ).mapN { (_, _, _, _, _) =>
          val consumer = Consumer.create(streamArn, consumerName)
          (
            streams.updateStream(
              stream
                .copy(consumers = stream.consumers + (consumerName -> consumer))
            ),
            RegisterStreamConsumerResponse(consumer)
          )
        }
      )
}

object RegisterStreamConsumerRequest {
  implicit val registerStreamConsumerRequestEncoder
      : Encoder[RegisterStreamConsumerRequest] =
    Encoder.forProduct2("ConsumerName", "StreamARN")(x =>
      (x.consumerName, x.streamArn)
    )
  implicit val registerStreamConsumerRequestDecoder
      : Decoder[RegisterStreamConsumerRequest] = { x =>
    for {
      consumerName <- x.downField("ConsumerName").as[ConsumerName]
      streamArn <- x.downField("StreamARN").as[String]
    } yield RegisterStreamConsumerRequest(consumerName, streamArn)
  }
  implicit val registerStreamConsumerRequestEq
      : Eq[RegisterStreamConsumerRequest] = Eq.fromUniversalEquals
}
