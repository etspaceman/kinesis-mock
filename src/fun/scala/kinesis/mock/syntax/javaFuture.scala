package kinesis.mock.syntax

import scala.jdk.FutureConverters._

import java.util.concurrent.CompletionStage

import cats.effect.{ContextShift, IO}

object javaFuture extends JavaFutureSyntax

trait JavaFutureSyntax {
  implicit def toJavaFutureOps[A](
      future: => CompletionStage[A]
  ): JavaFutureSyntax.JavaFutureOps[A] =
    new JavaFutureSyntax.JavaFutureOps(future)
}

object JavaFutureSyntax {
  final class JavaFutureOps[A](future: => CompletionStage[A]) {
    def toIO(implicit CS: ContextShift[IO]): IO[A] =
      IO.fromFuture(IO(future.asScala))
  }
}
