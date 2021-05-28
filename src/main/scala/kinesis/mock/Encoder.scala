package kinesis.mock

import io.bullet.borer
import io.circe

trait Encoder[A] {
  def circeEncoder: circe.Encoder[A]
  def circeCborEncoder: circe.Encoder[A]
  lazy val borerEncoder: borer.Encoder[A] =
    kinesis.mock.instances.borer.borerEncoderFromCirceEncoder(circeCborEncoder)
}

object Encoder {
  def apply[A](implicit E: Encoder[A]): Encoder[A] = E
  def instance[A](
      circeEncoder0: circe.Encoder[A],
      circeCborEncoder0: circe.Encoder[A]
  ): Encoder[A] = new Encoder[A] {
    override val circeEncoder: circe.Encoder[A] = circeEncoder0
    override val circeCborEncoder: circe.Encoder[A] = circeCborEncoder0
  }
  def derive[A](implicit circeEncoder0: circe.Encoder[A]): Encoder[A] =
    instance(circeEncoder0, circeEncoder0)
}
