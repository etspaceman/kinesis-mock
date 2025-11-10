package kinesis.mock.regexp

import ast.*
import org.scalacheck.{Arbitrary, Gen}

object ASTProcessor:

  // TODO negation which doesn't use `suchThat`?
  private def negated(
      re: Negated
  )(using ev: Arbitrary[Char]): Gen[String] =

    val arbitraryString: Gen[String] =
      Arbitrary.arbitrary[Char].map(_.toString)

    def termToString(term: CharacterClass.Term): String =
      term match
        case CharacterClass.Literal(str) =>
          str
        case CharacterClass.CharRange(min, max) =>
          s"$min-$max"
        case CharacterClass.DigitRange(min, max) =>
          s"$min-$max"
        case CharacterClass.DigitChar =>
          "\\d"
        case CharacterClass.WordChar =>
          "\\w"
        case CharacterClass.SpaceChar =>
          "\\s"
        case _ =>
          ""

    re match
      case Negated(WordChar) =>
        arbitraryString.suchThat(_.matches("\\W"))
      case Negated(DigitChar) =>
        arbitraryString.suchThat(_.matches("\\D"))
      case Negated(SpaceChar) =>
        arbitraryString.suchThat(_.matches("\\S"))
      case Negated(WordBoundary) =>
        Gen.const("")
      case Negated(CharacterClass(terms*)) =>
        arbitraryString.suchThat(
          _.matches(s"[^${terms.map(termToString).mkString("")}]")
        )
      // TODO fix AST so that this isn't a valid construction
      case _ =>
        sys.error("invalid negated term")

  // TODO tailrec optimisation
  def apply(re: RegularExpression)(using ev: Arbitrary[Char]): Gen[String] =
    re match
      case Literal(str) =>
        literal(str)
      case WordChar =>
        wordChar
      case SpaceChar =>
        spaceChar
      case DigitChar =>
        digitChar
      case AnyChar =>
        Arbitrary.arbitrary[Char].map(_.toString)
      case Group(inner) =>
        apply(inner)
      case NonCapturingGroup(inner) =>
        apply(inner)
      case Or(left, right) =>
        Gen.oneOf(apply(left), apply(right))
      case And(left, right) =>
        for
          l <- apply(left)
          r <- apply(right)
        yield l + r
      case Optional(inner) =>
        optional(apply(inner))
      case OneOrMore(inner) =>
        Gen.nonEmptyListOf(apply(inner)).map(_.mkString(""))
      case ZeroOrMore(inner) =>
        Gen.listOf(apply(inner)).map(_.mkString(""))
      case RangeFrom(inner, min) =>
        // configurable defaults
        for
          length <- Gen.choose(min, 100)
          list <- Gen.listOfN(length, apply(inner))
        yield list.mkString("")
      case Range(inner, min, max) =>
        for
          length <- Gen.choose(min, max)
          list <- Gen.listOfN(length, apply(inner))
        yield list.mkString("")
      case Length(inner, length) =>
        Gen.listOfN(length, apply(inner)).map(_.mkString(""))
      case CharacterClass(terms*) =>
        processClass(terms)
      case term: Negated =>
        negated(term)
      case _: Substitution =>
        sys.error("backreferences are not supported")
      case WordBoundary | BOS | EOS =>
        Gen.const("")

  private def processClass(terms: Seq[CharacterClass.Term]): Gen[String] =

    val gens = terms.toList.map {
      case CharacterClass.Literal(str) =>
        literal(str)
      case CharacterClass.DigitRange(min, max) =>
        Gen.choose(min, max).map(_.toString)
      case CharacterClass.CharRange(min, max) =>
        Gen.choose(min, max).map(_.toString)
      case CharacterClass.WordChar =>
        wordChar
      case CharacterClass.SpaceChar =>
        spaceChar
      case CharacterClass.DigitChar =>
        digitChar
      case _ =>
        Gen.const("")
    }

    gens match
      case a :: Nil     => a
      case a :: b :: xs =>
        Gen.oneOf(a, b, xs*)
      case _ =>
        Gen.const("")

  private val wordChar: Gen[String] =
    Gen.oneOf(Gen.alphaNumChar, Gen.const('_')).map(_.toString)

  private val spaceChar: Gen[String] =
    // should this contain other characters?
    Gen.oneOf(" ", "\t")

  private val digitChar: Gen[String] =
    Gen.numChar.map(_.toString)

  private def literal(str: String): Gen[String] =
    Gen.const(str)

  private def optional(inner: Gen[String]): Gen[String] =
    Gen.frequency(1 -> inner, 1 -> Gen.const(""))
