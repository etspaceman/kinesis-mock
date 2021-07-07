package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_RegisterStreamConsumer.html
final case class RegisterStreamConsumerRequest(
    consumerName: ConsumerName,
    streamArn: String
) {
  def registerStreamConsumer(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[RegisterStreamConsumerResponse]] =
    streamsRef.modify { streams =>
      CommonValidations
        .validateStreamArn(streamArn)
        .flatMap(_ =>
          CommonValidations
            .findStreamByArn(streamArn, streams)
            .flatMap(stream =>
              (
                CommonValidations.validateConsumerName(consumerName),
                if (stream.consumers.size >= 20)
                  LimitExceededException(
                    s"Only 20 consumers can be registered to a stream at once"
                  ).asLeft
                else Right(()),
                if (
                  stream.consumers.values
                    .count(_.consumerStatus == ConsumerStatus.CREATING) >= 5
                )
                  LimitExceededException(
                    s"Only 5 consumers can be created at the same time"
                  ).asLeft
                else Right(()),
                if (stream.consumers.contains(consumerName))
                  ResourceInUseException(
                    s"Consumer $consumerName exists"
                  ).asLeft
                else Right(())
              ).mapN { (_, _, _, _) => (stream, streamArn, consumerName) }
            )
        )
        .map { case (stream, streamArn, consumerName) =>
          val consumer = Consumer.create(streamArn, consumerName)

          (
            streams.updateStream(
              stream
                .copy(consumers = stream.consumers + (consumerName -> consumer))
            ),
            RegisterStreamConsumerResponse(
              ConsumerSummary.fromConsumer(consumer)
            )
          )
        }
        .sequenceWithDefault(streams)
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
