package kinesis.mock.retry

import scala.concurrent.duration.FiniteDuration

import cats.effect.Temporal

trait Sleep[M[_]]:
  def sleep(delay: FiniteDuration): M[Unit]

object Sleep:
  def apply[M[_]](using sleep: Sleep[M]): Sleep[M] = sleep

  given sleepUsingTemporal[F[_]](using t: Temporal[F]): Sleep[F] =
    (delay: FiniteDuration) => t.sleep(delay)
