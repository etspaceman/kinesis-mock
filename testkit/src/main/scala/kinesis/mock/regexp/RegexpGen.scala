package kinesis.mock.regexp

import org.scalacheck.{Arbitrary, Gen}

object RegexpGen {

  def from(str: String)(implicit ev: Arbitrary[Char]): Gen[String] =
    ASTProcessor(GenParser.parse(str))
}
