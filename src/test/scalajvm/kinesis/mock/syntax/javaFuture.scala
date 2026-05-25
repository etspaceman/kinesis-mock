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
