package kinesis.mock.models

import scala.math.Ordering

import cats.Eq
import io.circe._

final case class ConsumerName(consumerName: String) {
  override def toString: String = consumerName
}

object ConsumerName {
  implicit val consumerNameOrdering: Ordering[ConsumerName] =
    (x: ConsumerName, y: ConsumerName) =>
      Ordering[String].compare(x.consumerName, y.consumerName)
  implicit val consumerNameCirceEncoder: Encoder[ConsumerName] =
    Encoder[String].contramap(_.consumerName)
  implicit val consumerNameCirceDecoder: Decoder[ConsumerName] =
    Decoder[String].map(ConsumerName.apply)
  implicit val consumerNameCirceKeyEncoder: KeyEncoder[ConsumerName] =
    KeyEncoder[String].contramap(_.consumerName)
  implicit val consumerNameCirceKeyDecoder: KeyDecoder[ConsumerName] =
    KeyDecoder[String].map(ConsumerName.apply)
  implicit val consumerNameEq: Eq[ConsumerName] = Eq.fromUniversalEquals
}
