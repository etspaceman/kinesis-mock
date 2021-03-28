package kinesis.mock.syntax

import cats.effect.IO
import cats.effect.concurrent.Semaphore

object semaphore extends SemaphoreSyntax

trait SemaphoreSyntax {
  implicit def toSemaphoreOps(
      semaphore: Semaphore[IO]
  ): SemaphoreSyntax.SemaphoreOps =
    new SemaphoreSyntax.SemaphoreOps(semaphore)
}

object SemaphoreSyntax {
  final class SemaphoreOps(private val semaphore: Semaphore[IO])
      extends AnyVal {
    def tryAcquireRelease[A](successIO: IO[A], failureIO: IO[A]): IO[A] =
      semaphore.tryAcquire.bracket {
        case true  => successIO
        case false => failureIO
      } {
        case true  => semaphore.release
        case false => IO.unit
      }
  }
}
