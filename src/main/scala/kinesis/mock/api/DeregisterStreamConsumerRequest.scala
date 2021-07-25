package kinesis.mock
package api

import cats.Eq
import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.syntax.either._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DeregisterStreamConsumer.html
final case class DeregisterStreamConsumerRequest(
    consumerArn: Option[String],
    consumerName: Option[ConsumerName],
    streamArn: Option[String]
) {
  private def deregister(
      streams: Streams,
      consumer: Consumer,
      stream: StreamData
  ): (Streams, Consumer) = {
    val newConsumer =
      consumer.copy(consumerStatus = ConsumerStatus.DELETING)

    (
      streams.updateStream(
        stream.copy(consumers =
          stream.consumers ++ Seq(consumer.consumerName -> newConsumer)
        )
      ),
      newConsumer
    )
  }

  def deregisterStreamConsumer(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[Consumer]] = streamsRef.modify { streams =>
    (consumerArn, consumerName, streamArn) match {
      case (Some(cArn), _, _) =>
        CommonValidations
          .findStreamByConsumerArn(cArn, streams)
          .flatMap {
            case (consumer, stream)
                if consumer.consumerStatus == ConsumerStatus.ACTIVE =>
              (consumer, stream).asRight
            case _ =>
              ResourceInUseException(
                s"Consumer $consumerName is not in an ACTIVE state"
              ).asLeft
          }
          .map { case (consumer, stream) =>
            deregister(streams, consumer, stream)
          }
          .sequenceWithDefault(streams)
      case (None, Some(cName), Some(sArn)) =>
        CommonValidations
          .findStreamByArn(sArn, streams)
          .flatMap { stream =>
            CommonValidations.findConsumer(cName, stream).flatMap {
              case consumer
                  if consumer.consumerStatus == ConsumerStatus.ACTIVE =>
                (consumer, stream).asRight
              case _ =>
                ResourceInUseException(
                  s"Consumer $consumerName is not in an ACTIVE state"
                ).asLeft

            }
          }
          .map { case (consumer, stream) =>
            deregister(streams, consumer, stream)
          }
          .sequenceWithDefault(streams)
      case _ =>
        (
          streams,
          InvalidArgumentException(
            "ConsumerArn or both ConsumerName and StreamARN are required for this request."
          ).asLeft
        )
    }
  }
}

object DeregisterStreamConsumerRequest {
  implicit val deregisterStreamConsumerRequestCirceEncoder
      : circe.Encoder[DeregisterStreamConsumerRequest] =
    circe.Encoder.forProduct3("ConsumerARN", "ConsumerName", "StreamARN")(x =>
      (x.consumerArn, x.consumerName, x.streamArn)
    )
  implicit val deregisterStreamConsumerRequestCirceDecoder
      : circe.Decoder[DeregisterStreamConsumerRequest] = { x =>
    for {
      consumerArn <- x.downField("ConsumerARN").as[Option[String]]
      consumerName <- x.downField("ConsumerName").as[Option[ConsumerName]]
      streamArn <- x.downField("StreamARN").as[Option[String]]
    } yield DeregisterStreamConsumerRequest(
      consumerArn,
      consumerName,
      streamArn
    )
  }
  implicit val deregisterStreamConsumerRequestEncoder
      : Encoder[DeregisterStreamConsumerRequest] =
    Encoder.derive
  implicit val deregisterStreamConsumerRequestDecoder
      : Decoder[DeregisterStreamConsumerRequest] =
    Decoder.derive
  implicit val deregisterStreamConsumerEq: Eq[DeregisterStreamConsumerRequest] =
    Eq.fromUniversalEquals
}
