package kinesis.mock
package models

import java.time.Instant

import cats.Eq
import io.circe

import kinesis.mock.instances.circe._

final case class Consumer(
    consumerArn: String,
    consumerCreationTimestamp: Instant,
    consumerName: ConsumerName,
    consumerStatus: ConsumerStatus,
    streamArn: String
)

object Consumer {
  def create(streamArn: String, consumerName: ConsumerName): Consumer = {
    val consumerCreationTimestamp = Instant.now()
    Consumer(
      s"$streamArn/consumer/$consumerName:${consumerCreationTimestamp.getEpochSecond}",
      consumerCreationTimestamp,
      consumerName,
      ConsumerStatus.CREATING,
      streamArn
    )
  }
  def consumerCirceEncoder(implicit
      EI: circe.Encoder[Instant]
  ): circe.Encoder[Consumer] = circe.Encoder.forProduct5(
    "ConsumerARN",
    "ConsumerCreationTimestamp",
    "ConsumerName",
    "ConsumerStatus",
    "StreamARN"
  )(x =>
    (
      x.consumerArn,
      x.consumerCreationTimestamp,
      x.consumerName,
      x.consumerStatus,
      x.streamArn
    )
  )

  def consumerCirceDecoder(implicit
      DI: circe.Decoder[Instant]
  ): circe.Decoder[Consumer] = { x =>
    for {
      consumerArn <- x.downField("ConsumerARN").as[String]
      consumerCreationTimestamp <- x
        .downField("ConsumerCreationTimestamp")
        .as[Instant]
      consumerName <- x.downField("ConsumerName").as[ConsumerName]
      consumerStatus <- x.downField("ConsumerStatus").as[ConsumerStatus]
      streamArn <- x.downField("StreamARN").as[String]
    } yield Consumer(
      consumerArn,
      consumerCreationTimestamp,
      consumerName,
      consumerStatus,
      streamArn
    )
  }

  implicit val consumerEncoder: Encoder[Consumer] = Encoder.instance(
    consumerCirceEncoder(instantDoubleCirceEncoder),
    consumerCirceEncoder(instantLongCirceEncoder)
  )

  implicit val consumerDecoder: Decoder[Consumer] = Decoder.instance(
    consumerCirceDecoder(instantDoubleCirceDecoder),
    consumerCirceDecoder(instantLongCirceDecoder)
  )

  implicit val consumerEq: Eq[Consumer] = (x, y) =>
    x.consumerArn == y.consumerArn &&
      x.consumerCreationTimestamp.getEpochSecond == y.consumerCreationTimestamp.getEpochSecond &&
      x.consumerName == y.consumerName &&
      x.consumerStatus == y.consumerStatus &&
      x.streamArn == y.streamArn
}
