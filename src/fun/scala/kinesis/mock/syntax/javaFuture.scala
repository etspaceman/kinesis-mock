package kinesis.mock.syntax

import scala.jdk.FutureConverters._

import java.util.concurrent.CompletionStage

import cats.effect.{ContextShift, IO}
import com.google.common.util.concurrent.ListenableFuture
import scala.concurrent.Future
import scala.concurrent.Promise
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.FutureCallback
import java.util.concurrent.Executor

object javaFuture extends JavaFutureSyntax

trait JavaFutureSyntax {
  implicit def toJavaFutureOps[A](
      future: => CompletionStage[A]
  ): JavaFutureSyntax.JavaFutureOps[A] =
    new JavaFutureSyntax.JavaFutureOps(future)

  implicit def toListenableFutureOps[A](
      future: => ListenableFuture[A]
  ): JavaFutureSyntax.ListenableFutureOps[A] =
    new JavaFutureSyntax.ListenableFutureOps(future)
}

object JavaFutureSyntax {
  final class JavaFutureOps[A](future: => CompletionStage[A]) {
    def toIO(implicit CS: ContextShift[IO]): IO[A] =
      IO.fromFuture(IO(future.asScala))
  }

  final class ListenableFutureOps[A](future: => ListenableFuture[A]) {
    def asScala(implicit e: Executor): Future[A] = {
      val p = Promise[A]()
      Futures.addCallback(
        future,
        new FutureCallback[A] {
          def onSuccess(result: A): Unit = {
            val _ = p.success(result)
          }

          def onFailure(t: Throwable): Unit = {
            val _ = p.failure(t)
          }
        },
        e
      )
      p.future
    }

    def toIO(implicit E: Executor, CS: ContextShift[IO]): IO[A] =
      IO.fromFuture(IO(asScala))
  }
}
