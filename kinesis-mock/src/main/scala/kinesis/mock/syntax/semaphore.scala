/*
 * Copyright 2021-2023 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package kinesis.mock.syntax

import cats.effect.IO
import cats.effect.std.Semaphore

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
