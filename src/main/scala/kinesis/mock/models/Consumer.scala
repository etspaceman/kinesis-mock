package kinesis.mock.models

import java.time.Instant

import cats.kernel.Eq
import io.circe._

import kinesis.mock.instances.circe._

final case class Consumer(
    consumerArn: String,
    consumerCreationTimestamp: Instant,
    consumerName: ConsumerName,
    consumerStatus: ConsumerStatus
)

object Consumer {
  def create(streamArn: String, consumerName: ConsumerName): Consumer = {
    val consumerCreationTimestamp = Instant.now()
    Consumer(
      s"$streamArn/consumer/$consumerName:${consumerCreationTimestamp.getEpochSecond}",
      consumerCreationTimestamp,
      consumerName,
      ConsumerStatus.CREATING
    )
  }
  implicit val consumerCirceEncoder: Encoder[Consumer] = Encoder.forProduct4(
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

  implicit val consumerCirceDecoder: Decoder[Consumer] = { x =>
    for {
      consumerArn <- x.downField("ConsumerARN").as[String]
      consumerCreationTimestamp <- x
        .downField("ConsumerCreationTimestamp")
        .as[Instant]
      consumerName <- x.downField("ConsumerName").as[ConsumerName]
      consumerStatus <- x.downField("ConsumerStatus").as[ConsumerStatus]
    } yield Consumer(
      consumerArn,
      consumerCreationTimestamp,
      consumerName,
      consumerStatus
    )
  }

  implicit val consumerEq: Eq[Consumer] = (x, y) =>
    x.consumerArn == y.consumerArn &&
      x.consumerCreationTimestamp.getEpochSecond == y.consumerCreationTimestamp.getEpochSecond &&
      x.consumerName == y.consumerName &&
      x.consumerStatus == y.consumerStatus
}
