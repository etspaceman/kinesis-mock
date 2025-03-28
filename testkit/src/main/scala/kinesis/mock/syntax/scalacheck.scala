package kinesis.mock.syntax

import org.scalacheck.Gen

object scalacheck extends ScalacheckSyntax

trait ScalacheckSyntax:
  implicit def toScalacheckGenOps[A](
      gen: Gen[A]
  ): ScalacheckSyntax.ScalacheckGenOps[A] =
    new ScalacheckSyntax.ScalacheckGenOps(gen)

object ScalacheckSyntax:
  final class ScalacheckGenOps[A](private val gen: Gen[A]) extends AnyVal:
    def lazyList: LazyList[A] = LazyList.continually(gen.sample.toList).flatten
    def one: A = lazyList.head
    def take(n: Int): Seq[A] = lazyList.take(n)
