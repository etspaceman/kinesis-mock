package kinesis.mock.regexp.ast

sealed trait RegularExpression {

  def |(that: RegularExpression): RegularExpression =
    Or(this, that)

  def &(that: RegularExpression): RegularExpression =
    And(this, that)
}

case class Literal(value: String) extends RegularExpression

case object WordChar extends RegularExpression
case object DigitChar extends RegularExpression
case object SpaceChar extends RegularExpression
case object AnyChar extends RegularExpression

case object BOS extends RegularExpression
case object EOS extends RegularExpression
case object WordBoundary extends RegularExpression

case class Group(term: RegularExpression) extends RegularExpression
case class Substitution(index: Int) extends RegularExpression
case class NonCapturingGroup(term: RegularExpression) extends RegularExpression

case class Or(t1: RegularExpression, t2: RegularExpression)
    extends RegularExpression
case class And(t1: RegularExpression, t2: RegularExpression)
    extends RegularExpression

case class Negated(term: RegularExpression) extends RegularExpression

sealed trait Quantified extends RegularExpression
case class Optional(term: RegularExpression) extends Quantified
case class ZeroOrMore(term: RegularExpression) extends Quantified
case class OneOrMore(term: RegularExpression) extends Quantified
case class Length(term: RegularExpression, min: Int) extends Quantified
case class RangeFrom(term: RegularExpression, min: Int) extends Quantified
case class Range(term: RegularExpression, min: Int, max: Int) extends Quantified

object CharacterClass {

  sealed trait Term

  case class Literal(value: String) extends Term

  case class DigitRange(min: Int, max: Int) extends Term
  case class CharRange(min: Char, max: Char) extends Term

  case object WordChar extends Term
  case object DigitChar extends Term
  case object SpaceChar extends Term
  case object WordBoundary extends Term
}

case class CharacterClass(terms: CharacterClass.Term*) extends RegularExpression
