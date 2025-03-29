package kinesis.mock.syntax

object id extends IdSyntax

trait IdSyntax:
  implicit def toIdOps[A](a: A): IdSyntax.IdOps[A] = new IdSyntax.IdOps(a)

object IdSyntax:
  final class IdOps[A](private val a: A) extends AnyVal:
    def maybeTransform[B](opt: Option[B])(f: (A, B) => A): A =
      opt.foldLeft(a)(f)
