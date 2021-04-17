package kinesis.mock
package api

import cats.effect.IO
import cats.effect.concurrent.Ref
import cats.kernel.Eq
import cats.syntax.all._
import io.circe._

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
  ): IO[ValidatedResponse[DescribeStreamConsumerResponse]] =
    streamsRef.get.map { streams =>
      (consumerArn, consumerName, streamArn) match {
        case (Some(cArn), _, _) =>
          CommonValidations.findStreamByConsumerArn(cArn, streams).map {
            case (consumer, _) => DescribeStreamConsumerResponse(consumer)
          }
        case (None, Some(cName), Some(sArn)) =>
          CommonValidations.findStreamByArn(sArn, streams).andThen { stream =>
            CommonValidations
              .findConsumer(cName, stream)
              .map(DescribeStreamConsumerResponse.apply)
          }
        case _ =>
          InvalidArgumentException(
            "ConsumerArn or both ConsumerName and StreamARN are required for this request."
          ).invalidNel
      }
    }
}

object DescribeStreamConsumerRequest {
  implicit val describeStreamConsumerRequestEncoder
      : Encoder[DescribeStreamConsumerRequest] =
    Encoder.forProduct3("ConsumerARN", "ConsumerName", "StreamARN")(x =>
      (x.consumerArn, x.consumerName, x.streamArn)
    )
  implicit val describeStreamConsumerRequestDecoder
      : Decoder[DescribeStreamConsumerRequest] = { x =>
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
  implicit val describeStreamConsumerEq: Eq[DescribeStreamConsumerRequest] =
    Eq.fromUniversalEquals
}
