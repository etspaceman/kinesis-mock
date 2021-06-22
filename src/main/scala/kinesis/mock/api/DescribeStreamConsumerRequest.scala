package kinesis.mock
package api

import cats.Eq
import cats.effect.{IO, Ref}
import cats.syntax.all._
import io.circe

import kinesis.mock.models._
import kinesis.mock.validations.CommonValidations

// https://docs.aws.amazon.com/kinesis/latest/APIReference/API_DescribeStreamConsumer.html
final case class DescribeStreamConsumerRequest(
    consumerArn: Option[String],
    consumerName: Option[ConsumerName],
    streamArn: Option[String]
) {
  def describeStreamConsumer(
      streamsRef: Ref[IO, Streams]
  ): IO[Response[DescribeStreamConsumerResponse]] =
    streamsRef.get.map { streams =>
      (consumerArn, consumerName, streamArn) match {
        case (Some(cArn), _, _) =>
          CommonValidations.findStreamByConsumerArn(cArn, streams).map {
            case (consumer, _) => DescribeStreamConsumerResponse(consumer)
          }
        case (None, Some(cName), Some(sArn)) =>
          CommonValidations.findStreamByArn(sArn, streams).flatMap { stream =>
            CommonValidations
              .findConsumer(cName, stream)
              .map(DescribeStreamConsumerResponse.apply)
          }
        case _ =>
          InvalidArgumentException(
            "ConsumerArn or both ConsumerName and StreamARN are required for this request."
          ).asLeft
      }
    }
}

object DescribeStreamConsumerRequest {
  implicit val describeStreamConsumerRequestCirceEncoder
      : circe.Encoder[DescribeStreamConsumerRequest] =
    circe.Encoder.forProduct3("ConsumerARN", "ConsumerName", "StreamARN")(x =>
      (x.consumerArn, x.consumerName, x.streamArn)
    )
  implicit val describeStreamConsumerRequestCirceDecoder
      : circe.Decoder[DescribeStreamConsumerRequest] = { x =>
    for {
      consumerArn <- x.downField("ConsumerARN").as[Option[String]]
      consumerName <- x.downField("ConsumerName").as[Option[ConsumerName]]
      streamArn <- x.downField("StreamARN").as[Option[String]]
    } yield DescribeStreamConsumerRequest(
      consumerArn,
      consumerName,
      streamArn
    )
  }
  implicit val describeStreamConsumerRequestEncoder
      : Encoder[DescribeStreamConsumerRequest] =
    Encoder.derive
  implicit val describeStreamConsumerRequestDecoder
      : Decoder[DescribeStreamConsumerRequest] =
    Decoder.derive
  implicit val describeStreamConsumerEq: Eq[DescribeStreamConsumerRequest] =
    Eq.fromUniversalEquals
}
