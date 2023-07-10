package kinesis.mock.regexp

import scala.util.parsing.combinator.{PackratParsers, RegexParsers}
import scala.util.parsing.input.CharSequenceReader
import ast._

object GenParser extends RegexParsers with PackratParsers {

  override def skipWhitespace: Boolean = false

  lazy val expression0: PackratParser[RegularExpression] = group | characterClass | term

  lazy val expression1: PackratParser[RegularExpression] = {

    val int: Parser[Int] = "\\d+".r ^^ { _.toInt }

    // annoyingly hacky :( and it potentially breaks associativity but it's
    // nicer than having a concat rule for our purposes
    def splitLiteral(re: RegularExpression, result: RegularExpression => RegularExpression): RegularExpression = {
      re match {
        case Literal(s) if s.length > 1 =>
          And(Literal(s.init), result(Literal(s.last.toString)))
        case other =>
          result(other)
      }
    }

    // quantifiers
    val optional   = expression0 <~ "?" ^^ { splitLiteral(_, Optional.apply) }
    val oneOrMore  = expression0 <~ "+" ^^ { splitLiteral(_, OneOrMore.apply) }
    val zeroOrMore = expression0 <~ "*" ^^ { splitLiteral(_, ZeroOrMore.apply) }

    val rangeFrom = expression0 ~ ("{" ~> int <~ ",}") ^^ {
      case expr ~ min =>
        splitLiteral(expr, RangeFrom(_, min))
    }

    val range = expression0 ~ ("{" ~> int ~ ("," ~> int <~ "}")) ^^ {
      case expr ~ (min ~ max) =>
        splitLiteral(expr, Range(_, min, max))
    }

    val length = expression0 ~ ("{" ~> int <~ "}") ^^ {
      case expr ~ l =>
        splitLiteral(expr, Length(_, l))
    }

    optional | oneOrMore | zeroOrMore | rangeFrom | range | length | expression0
  }

  // and
  lazy val expression2: PackratParser[RegularExpression] =
    (expression2 ~ expression1) ^^ { case a ~ b => And(a, b) } | expression1

  // or
  lazy val expression: PackratParser[RegularExpression] =
    (expression ~ ("|" ~> expression2) ^^ { case a ~ b => Or(a, b) }) | expression2

  lazy val group: PackratParser[RegularExpression] = {

    lazy val nonCapturingGroup = "(?:" ~> expression <~ ")" ^^ NonCapturingGroup.apply
    lazy val capturingGroup    = "(" ~> expression <~ ")" ^^ Group.apply

    capturingGroup | nonCapturingGroup
  }


  // character classes
  lazy val characterClass: PackratParser[RegularExpression] = {

    lazy val digitRange: Parser[CharacterClass.Term] = {
      val d: Parser[Int] = "\\d".r ^^ { _.toInt }
      (d ~ ("-" ~> d)) ^^ {
        case min ~ max =>
          CharacterClass.DigitRange(min, max)
      }
    }

    lazy val lowerAlphaRange: Parser[CharacterClass.Term] = {
      val c = "[a-z]".r ^^ { _.charAt(0) }
      c ~ ("-" ~> c) ^^ {
        case min ~ max =>
          CharacterClass.CharRange(min, max)
      }
    }

    lazy val upperAlphaRange: Parser[CharacterClass.Term] = {
      val c = "[A-Z]".r ^^ { _.charAt(0) }
      c ~ ("-" ~> c) ^^ {
        case min ~ max =>
          CharacterClass.CharRange(min, max)
      }
    }

    val word: Parser[CharacterClass.Term]         = "\\w" ^^^ CharacterClass.WordChar
    val digit: Parser[CharacterClass.Term]        = "\\d" ^^^ CharacterClass.DigitChar
    val space: Parser[CharacterClass.Term]        = "\\s" ^^^ CharacterClass.SpaceChar
    val wordBoundary: Parser[CharacterClass.Term] = "\\b" ^^^ CharacterClass.WordBoundary

    lazy val char: Parser[CharacterClass.Term] = {
      val normalChars = "[^\\]\\\\]".r
      val meta = "\\" | "]" | "-"
      (("\\" ~> meta) | normalChars | "\\" ~> normalChars) ^^ CharacterClass.Literal.apply
    }

    lazy val characterClassTerm: Parser[CharacterClass.Term] =
      word | digit | space | wordBoundary | digitRange | lowerAlphaRange | upperAlphaRange | char

    lazy val charClass =
      ("[" ~> characterClassTerm.+ <~ "]") ^^ { CharacterClass(_: _*) }
    lazy val negatedCharClass =
      ("[^" ~> characterClassTerm.+ <~ "]") ^^ { terms => Negated(CharacterClass(terms: _*)) }

    negatedCharClass | charClass
  }

  // terminals...
  lazy val term: PackratParser[RegularExpression] = char | classes | negClasses | substitution

  // default classes
  lazy val word: Parser[RegularExpression]         = "\\w" ^^^ WordChar
  lazy val digit: Parser[RegularExpression]        = "\\d" ^^^ DigitChar
  lazy val space: Parser[RegularExpression]        = "\\s" ^^^ SpaceChar
  lazy val any: Parser[RegularExpression]          = "."   ^^^ AnyChar
  lazy val wordBoundary: Parser[RegularExpression] = "\\b" ^^^ WordBoundary
  lazy val classes: Parser[RegularExpression]      = word | digit | space | any | wordBoundary

  lazy val negWord: Parser[RegularExpression]      = "\\W" ^^^ Negated(WordChar)
  lazy val negDigit: Parser[RegularExpression]     = "\\D" ^^^ Negated(DigitChar)
  lazy val negSpace: Parser[RegularExpression]     = "\\S" ^^^ Negated(SpaceChar)
  lazy val negBoundary: Parser[RegularExpression]  = "\\B" ^^^ Negated(WordBoundary)
  lazy val negClasses: Parser[RegularExpression]   = negWord | negDigit | negSpace | negBoundary

  lazy val substitution: Parser[RegularExpression] = "\\" ~> "[1-9]\\d*".r  ^^ {
    index =>
      Substitution(index.toInt)
  }

  lazy val bos: Parser[RegularExpression] = "^" ^^^ BOS
  lazy val eos: Parser[RegularExpression] = "$" ^^^ EOS

  lazy val regularExpression: Parser[RegularExpression] = {
    bos ~> expression <~ eos ^^ { expr => And(And(BOS, expr), EOS) } |
    bos ~> expression        ^^ { expr => And(BOS, expr) } |
    expression <~ eos        ^^ { expr => And(expr, EOS) } |
    expression
  }

  // literals
  lazy val char: PackratParser[Literal] = {
    val meta: Parser[String] = ")" | "(" | "$" | "[" | "." | "+" | "*" | "?" | "|" | "\\" | "{"
    (("\\" ~> meta) | "[^|)(.+*?{\\[$\\\\]".r).+ ^^ { strs => Literal(strs.mkString("")) }
  }

  def parse(string: String): RegularExpression = {
    regularExpression(new PackratReader(new CharSequenceReader(string))).get
  }
}
