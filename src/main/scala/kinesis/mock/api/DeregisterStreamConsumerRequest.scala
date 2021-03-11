package kinesis.mock
package api

import cats.data.Validated._
import cats.data._
import cats.syntax.all._
import io.circe._

import kinesis.mock.models._

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DeregisterStreamConsumer.html
final case class DeregisterStreamConsumerRequest(
    consumerArn: Option[String],
    consumerName: Option[String],
    streamArn: Option[String]
) {
  private def deregister(
      streams: Streams,
      consumer: Consumer,
      stream: StreamData
  ): ValidatedNel[
    KinesisMockException,
    (Streams, Consumer)
  ] = {
    val newConsumer = consumer.copy(consumerStatus = ConsumerStatus.DELETING)
    Valid(
      (
        streams.updateStream(
          stream.copy(consumers =
            stream.consumers + (consumer.consumerName -> newConsumer)
          )
        ),
        newConsumer
      )
    )
  }

  def deregisterStreamConsumer(
      streams: Streams
  ): ValidatedNel[
    KinesisMockException,
    (Streams, Consumer)
  ] = {
    (consumerArn, consumerName, streamArn) match {
      case (Some(cArn), _, _) =>
        CommonValidations.findStreamByConsumerArn(cArn, streams).andThen {
          case (consumer, stream)
              if (consumer.consumerStatus == ConsumerStatus.ACTIVE) =>
            deregister(streams, consumer, stream)
          case _ =>
            ResourceInUseException(
              s"Consumer $consumerName is not in an ACTIVE state"
            ).invalidNel
        }
      case (None, Some(cName), Some(sArn)) =>
        CommonValidations.findStreamByArn(sArn, streams).andThen { stream =>
          CommonValidations.findConsumer(cName, stream).andThen {
            case consumer if consumer.consumerStatus == ConsumerStatus.ACTIVE =>
              deregister(streams, consumer, stream)
            case _ =>
              ResourceInUseException(
                s"Consumer $consumerName is not in an ACTIVE state"
              ).invalidNel
          }
        }
      case _ =>
        InvalidArgumentException(
          "ConsumerArn or both ConsumerName and StreamARN are required for this request."
        ).invalidNel
    }
  }
}

object DeregisterStreamConsumerRequest {
  implicit val deregisterStreamConsumerRequestEncoder
      : Encoder[DeregisterStreamConsumerRequest] =
    Encoder.forProduct3("ConsumerARN", "ConsumerName", "StreamARN")(x =>
      (x.consumerArn, x.consumerName, x.streamArn)
    )
  implicit val deregisterStreamConsumerRequestDecoder
      : Decoder[DeregisterStreamConsumerRequest] = { x =>
    for {
      consumerArn <- x.downField("ConsumerARN").as[Option[String]]
      consumerName <- x.downField("ConsumerName").as[Option[String]]
      streamArn <- x.downField("StreamARN").as[Option[String]]
    } yield DeregisterStreamConsumerRequest(
      consumerArn,
      consumerName,
      streamArn
    )
  }
}
