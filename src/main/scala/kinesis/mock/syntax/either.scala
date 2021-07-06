package kinesis.mock.syntax

object either extends KinesisMockEitherSyntax

trait KinesisMockEitherSyntax {
  implicit def toKinesisMockEitherTupleOps[L, T1, T2](
      e: Either[L, (T1, T2)]
  ): KinesisMockEitherSyntax.KinesisMockEitherTupleOps[L, T1, T2] =
    new KinesisMockEitherSyntax.KinesisMockEitherTupleOps(e)
}

object KinesisMockEitherSyntax {
  final class KinesisMockEitherTupleOps[L, T1, T2](
      private val e: Either[L, (T1, T2)]
  ) extends AnyVal {
    def sequenceWithDefault(default: T1): (T1, Either[L, T2]) =
      e.fold(e => (default, Left(e)), { case (t1, t2) => (t1, Right(t2)) })
  }
}
