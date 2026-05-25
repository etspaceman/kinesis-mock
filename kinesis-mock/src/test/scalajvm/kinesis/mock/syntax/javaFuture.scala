/*
 * Copyright 2021-2026 io.github.etspaceman
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

import scala.concurrent.{Future, Promise}

import java.util.concurrent.{CompletableFuture, Executor}

import cats.effect.IO
import com.google.common.util.concurrent.{
  FutureCallback,
  Futures,
  ListenableFuture
}

object javaFuture extends JavaFutureSyntax

trait JavaFutureSyntax:
  extension [A](future: => CompletableFuture[A])
    def toIO: IO[A] =
      IO.fromCompletableFuture(IO(future))

  extension [A](future: => ListenableFuture[A])
    def asScala(using e: Executor): Future[A] =
      val p = Promise[A]()
      Futures.addCallback(
        future,
        new FutureCallback[A]:
          def onSuccess(result: A): Unit =
            val _ = p.success(result)

          def onFailure(t: Throwable): Unit =
            val _ = p.failure(t)
        ,
        e
      )
      p.future

    def toIO(using E: Executor): IO[A] =
      IO.fromFuture(IO(asScala))
