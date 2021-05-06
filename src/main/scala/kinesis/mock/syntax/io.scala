package kinesis.mock
package syntax

import cats.effect.IO
import cats.syntax.all._

object io extends KinesisMockIOSyntax

trait KinesisMockIOSyntax {
  implicit def toKinesisMockIOValidatedResultOps[A](
      io: IO[ValidatedResponse[A]]
  ): KinesisMockIOSyntax.KiensisMockIOValidatedResultOps[A] =
    new KinesisMockIOSyntax.KiensisMockIOValidatedResultOps(io)
}

object KinesisMockIOSyntax {
  final class KiensisMockIOValidatedResultOps[A](
      private val io: IO[ValidatedResponse[A]]
  ) extends AnyVal {
    def aggregateErrors: IO[EitherResponse[A]] =
      io.map(_.toEither.leftMap(KinesisMockException.aggregate))
  }
}
