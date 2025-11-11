package kinesis.mock.syntax

object id extends IdSyntax

trait IdSyntax:
  extension [A](a: A)
    def maybeTransform[B](opt: Option[B])(f: (A, B) => A): A =
      opt.foldLeft(a)(f)
