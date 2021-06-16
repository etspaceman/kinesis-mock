package kinesis.mock
package models

import java.time.Instant

import cats.kernel.Eq
import io.circe

import kinesis.mock.instances.circe._

final case class ConsumerSummary(
    consumerArn: String,
    consumerCreationTimestamp: Instant,
    consumerName: ConsumerName,
    consumerStatus: ConsumerStatus
)

object ConsumerSummary {
  def fromConsumer(consumer: Consumer): ConsumerSummary = ConsumerSummary(
    consumer.consumerArn,
    consumer.consumerCreationTimestamp,
    consumer.consumerName,
    consumer.consumerStatus
  )
  def consumerSummaryCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[ConsumerSummary] = circe.Encoder.forProduct4(
    "ConsumerARN",
    "ConsumerCreationTimestamp",
    "ConsumerName",
    "ConsumerStatus"
  )(x =>
    (
      x.consumerArn,
      x.consumerCreationTimestamp,
      x.consumerName,
      x.consumerStatus
    )
  )

  def consumerSummaryCirceDecoder(implicit
      DI: circe.Decoder[Instant]
  ): circe.Decoder[ConsumerSummary] = { x =>
    for {
      consumerArn <- x.downField("ConsumerARN").as[String]
      consumerCreationTimestamp <- x
        .downField("ConsumerCreationTimestamp")
        .as[Instant]
      consumerName <- x.downField("ConsumerName").as[ConsumerName]
      consumerStatus <- x.downField("ConsumerStatus").as[ConsumerStatus]
    } yield ConsumerSummary(
      consumerArn,
      consumerCreationTimestamp,
      consumerName,
      consumerStatus
    )
  }

  implicit val consumerSummaryEncoder: Encoder[ConsumerSummary] =
    Encoder.instance(
      consumerSummaryCirceEncoder(instantDoubleCirceEncoder),
      consumerSummaryCirceEncoder(instantLongCirceEncoder)
    )

  implicit val consumerSummaryDecoder: Decoder[ConsumerSummary] =
    Decoder.instance(
      consumerSummaryCirceDecoder(instantDoubleCirceDecoder),
      consumerSummaryCirceDecoder(instantLongCirceDecoder)
    )

  implicit val consumerSummaryEq: Eq[ConsumerSummary] = (x, y) =>
    x.consumerArn == y.consumerArn &&
      x.consumerCreationTimestamp.getEpochSecond == y.consumerCreationTimestamp.getEpochSecond &&
      x.consumerName == y.consumerName &&
      x.consumerStatus == y.consumerStatus
}
