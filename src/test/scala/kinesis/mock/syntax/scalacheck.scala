package kinesis.mock.syntax

import org.scalacheck.Gen

object scalacheck extends ScalacheckSyntax

trait ScalacheckSyntax:
  extension [A](gen: Gen[A])
    def lazyList: LazyList[A] = LazyList.continually(gen.sample.toList).flatten
    def one: A = lazyList.head
    def take(n: Int): Seq[A] = lazyList.take(n)
