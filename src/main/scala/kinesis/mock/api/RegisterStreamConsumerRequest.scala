package kinesis.mock
package api

import cats.data.Validated._
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.kernel.Eq
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_RegisterStreamConsumer.html
final case class RegisterStreamConsumerRequest(
    consumerName: ConsumerName,
    streamArn: String
) {
  def registerStreamConsumer(
      streamsRef: Ref[IO, Streams]
  ): IO[ValidatedResponse[RegisterStreamConsumerResponse]] =
    streamsRef.get.flatMap { streams =>
      CommonValidations
        .validateStreamArn(streamArn)
        .andThen(_ =>
          CommonValidations
            .findStreamByArn(streamArn, streams)
            .andThen(stream =>
              (
                CommonValidations.validateConsumerName(consumerName),
                if (stream.consumers.size >= 20)
                  LimitExceededException(
                    s"Only 20 consumers can be registered to a stream at once"
                  ).invalidNel
                else Valid(()),
                if (
                  stream.consumers.values
                    .count(_.consumerStatus == ConsumerStatus.CREATING) >= 5
                )
                  LimitExceededException(
                    s"Only 5 consumers can be created at the same time"
                  ).invalidNel
                else Valid(()),
                if (stream.consumers.contains(consumerName))
                  ResourceInUseException(
                    s"Consumer $consumerName exists"
                  ).invalidNel
                else Valid(())
              ).mapN { (_, _, _, _) => (stream, streamArn, consumerName) }
            )
        )
        .traverse { case (stream, streamArn, consumerName) =>
          val consumer = Consumer.create(streamArn, consumerName)

          streamsRef
            .update(x =>
              x.updateStream(
                stream
                  .copy(consumers =
                    stream.consumers ++ List(consumerName -> consumer)
                  )
              )
            )
            .as(
              RegisterStreamConsumerResponse(
                ConsumerSummary.fromConsumer(consumer)
              )
            )
        }
    }
}

object RegisterStreamConsumerRequest {
  implicit val registerStreamConsumerRequestCirceEncoder
      : circe.Encoder[RegisterStreamConsumerRequest] =
    circe.Encoder.forProduct2("ConsumerName", "StreamARN")(x =>
      (x.consumerName, x.streamArn)
    )
  implicit val registerStreamConsumerRequestCirceDecoder
      : circe.Decoder[RegisterStreamConsumerRequest] = { x =>
    for {
      consumerName <- x.downField("ConsumerName").as[ConsumerName]
      streamArn <- x.downField("StreamARN").as[String]
    } yield RegisterStreamConsumerRequest(consumerName, streamArn)
  }
  implicit val registerStreamConsumerRequestEncoder
      : Encoder[RegisterStreamConsumerRequest] = Encoder.derive
  implicit val registerStreamConsumerRequestDecoder
      : Decoder[RegisterStreamConsumerRequest] = Decoder.derive
  implicit val registerStreamConsumerRequestEq
      : Eq[RegisterStreamConsumerRequest] = Eq.fromUniversalEquals
}
