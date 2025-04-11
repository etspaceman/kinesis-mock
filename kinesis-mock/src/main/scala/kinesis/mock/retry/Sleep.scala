package kinesis.mock.retry

import scala.concurrent.duration.FiniteDuration

import cats.effect.Temporal

trait Sleep[M[_]]:
  def sleep(delay: FiniteDuration): M[Unit]

object Sleep:
  def apply[M[_]](implicit sleep: Sleep[M]): Sleep[M] = sleep

  implicit def sleepUsingTemporal[F[_]](implicit t: Temporal[F]): Sleep[F] =
    (delay: FiniteDuration) => t.sleep(delay)
