package kinesis.mock

import io.bullet.borer
import io.circe

trait Decoder[A] {
  def circeDecoder: circe.Decoder[A]
  def circeCborDecoder: circe.Decoder[A]
  lazy val borerDecoder: borer.Decoder[A] =
    kinesis.mock.instances.borer
      .defaultBorerDecoderFromCirceDecoder(circeCborDecoder)
}

object Decoder {
  def apply[A](implicit D: Decoder[A]): Decoder[A] = D
  def instance[A](
      circeDecoder0: circe.Decoder[A],
      circeCborDecoder0: circe.Decoder[A]
  ): Decoder[A] = new Decoder[A] {
    override val circeDecoder: circe.Decoder[A] = circeDecoder0
    override val circeCborDecoder: circe.Decoder[A] = circeCborDecoder0
  }
  def derive[A](implicit circeDecoder0: circe.Decoder[A]): Decoder[A] =
    instance(circeDecoder0, circeDecoder0)
}
