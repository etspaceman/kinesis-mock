package kinesis.mock.models

import java.time.Instant

import io.circe._

final case class Consumer(
    consumerArn: String,
    consumerCreationTimestamp: Instant,
    consumerName: String,
    consumerStatus: ConsumerStatus
)

object Consumer {
  val seqAdjustMs = 2000L
  def create(streamArn: String, consumerName: String): Consumer = {
    val consumerCreationTimestamp = Instant.now().minusMillis(seqAdjustMs)
    Consumer(
      s"$streamArn/consumer/$consumerName:${consumerCreationTimestamp.getEpochSecond()}",
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
      consumerName <- x.downField("ConsumerName").as[String]
      consumerStatus <- x.downField("ConsumerStatus").as[ConsumerStatus]
    } yield Consumer(
      consumerArn,
      consumerCreationTimestamp,
      consumerName,
      consumerStatus
    )
  }
}
