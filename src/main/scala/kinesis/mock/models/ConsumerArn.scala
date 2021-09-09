package kinesis.mock.models

import java.time.Instant

import scala.util.Try

import cats.Eq
import cats.syntax.all._
import io.circe._

final case class ConsumerArn(
    streamArn: StreamArn,
    consumerName: ConsumerName,
    creationTime: Instant
) {
  val consumerArn =
    s"$streamArn/consumer/$consumerName:${creationTime.getEpochSecond}"
  override def toString: String = consumerArn
}

object ConsumerArn {
  def fromArn(consumerArn: String): Either[String, ConsumerArn] = {
    for {
      streamArn <- Try(consumerArn.split("/consumer")(0)).toEither
        .leftMap(e =>
          s"Could not get stream arn part from consumer arn: ${e.getMessage}"
        )
        .flatMap(StreamArn.fromArn)
      consumerName <- Try(consumerArn.split("/")(3)).toEither.bimap(
        e => s"Could not get consumer name from ARN: ${e.getMessage}",
        ConsumerName.apply
      )
      consumerParts = consumerArn.split(":")
      creationTimestamp <- Try(consumerParts.last).toEither
        .leftMap(_.getMessage)
        .flatMap(x =>
          Try(x.toLong).toEither.leftMap(e =>
            s"Could not convert timestamp to Long: ${e.getMessage}"
          )
        )
        .flatMap(x =>
          Try(Instant.ofEpochSecond(x)).toEither
            .leftMap(e =>
              s"Could not convert timestamp from ARN to Instant: ${e.getMessage}"
            )
        )
    } yield ConsumerArn(streamArn, consumerName, creationTimestamp)
  }

  implicit val consumerArnCirceEncoder: Encoder[ConsumerArn] =
    Encoder[String].contramap(_.consumerArn)
  implicit val consumerArnCirceDecoder: Decoder[ConsumerArn] =
    Decoder[String].emap(ConsumerArn.fromArn)
  implicit val consumerArnCirceKeyEncoder: KeyEncoder[ConsumerArn] =
    KeyEncoder[String].contramap(_.consumerArn)
  implicit val consumerArnCirceKeyDecoder: KeyDecoder[ConsumerArn] =
    KeyDecoder.instance(ConsumerArn.fromArn(_).toOption)
  implicit val consumerArnEq: Eq[ConsumerArn] = Eq.fromUniversalEquals
  implicit val consumerArnOrdering: Ordering[ConsumerArn] =
    (x: ConsumerArn, y: ConsumerArn) =>
      Ordering[String].compare(x.consumerArn, y.consumerArn)
}
