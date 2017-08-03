package com.overviewdocs.query

import scala.util.parsing.combinator.RegexParsers

object QueryParser {
  def parse(input: String): Either[SyntaxError,Query] = {
    Grammar.parse(Grammar.phrase(Grammar.expression), input) match {
      case Grammar.Success(node, _) => Right(node)
      case Grammar.NoSuccess(msg, next) => Left(SyntaxError(msg, next.offset))
      case Grammar.Failure(msg, next) => Left(SyntaxError(msg, next.offset))
      case Grammar.Error(msg, next) => Left(SyntaxError(msg, next.offset))
    }
  }

  private object Grammar extends RegexParsers {
    override def skipWhitespace = false

    /*
     * These regexes limit length so that String.toInt can't throw. (There may
     * be other valid reasons for limiting length, but those aren't considered
     * here.)
     *
     * If the regexes fail, we fall back to a PhraseQuery.
     */
    private val FuzzyTermWithFuzz = """([^ ]*)~(\d{1,7})""".r
    private val FuzzyTermWithoutFuzz = """([^ ]*)~""".r
    private val ProximityPhrase = """(.*)~(\d{1,7})""".r // only makes sense after FuzzyTerm* fail
    private val Prefix = """(..+)\*""".r

    private def stringToNode(field: Field, s: String): Query = s match {
      case FuzzyTermWithFuzz(term, fuzzString) => FuzzyTermQuery(field, term, Some(fuzzString.toInt))
      case FuzzyTermWithoutFuzz(term) => FuzzyTermQuery(field, term, None)
      case ProximityPhrase(phrase, slopString) => ProximityQuery(field, phrase, slopString.toInt)
      case Prefix(prefix) => PrefixQuery(field, prefix)
      case _ => PhraseQuery(field, s)
    }

    def expression: Parser[Query] = chainl1(unaryExpression, binaryOperator)
    def unaryExpression: Parser[Query] = opt(whiteSpace) ~> (parensExpression | notExpression | fieldQuery) <~ opt(whiteSpace)

    def fieldQuery: Parser[FieldQuery]
      = fieldOrAll ~ stringToken ^^ { t => t._2.toQuery(t._1) }

    def fieldOrAll: Parser[Field]
      = (
        ("notes:" ^^^ Field.Notes) |
        ("title:" ^^^ Field.Title) |
        ("text:" ^^^ Field.Text) |
        ((quotedFieldName | unquotedFieldName) <~ ":" ^^ { s => Field.Metadata(s) }) |
        ("" ^^^ Field.All)
      )

    // String Token: a ton of text: everything until an operator or EOF
    //
    // "foo" bar => "foo" bar (completely consumed)
    // /path/to/file => /path/to/file (completely consumed)
    // "foo" AND bar => "foo" (leaving AND bar for later)
    // "foo" ) => "foo" (leaving ) for later)
    //
    // All this lets us parse `text:foo bar` as a single phrase query. Is it
    // worth it? Dunno, but this is what we've always done. (One could argue it
    // should be parsed as `text:foo AND bar`, similarly to Google.)
    trait StringToken {
      def toQuery(field: Field): FieldQuery

      /** Exact text that appears in the query */
      def rawValue: String

      /** Remove first and last char; remove backslashes from the rest */
      protected def dequote(s: String): String = {
        s
          .drop(1)
          .dropRight(1)
          .replaceAll("\\\\(.)", "$1")
      }
    }

    case class RegexString(rawValue: String) extends StringToken {
      override def toQuery(field: Field) = RegexQuery(field, dequote(rawValue))
    }

    case class QuotedString(rawValue: String) extends StringToken {
      override def toQuery(field: Field) = rawValue match {
        case ProximityPhrase(phrase, slopString) => ProximityQuery(field, dequote(phrase), slopString.toInt)
        case Prefix(prefix) => PrefixQuery(field, dequote(prefix))
        case _ => PhraseQuery(field, dequote(rawValue))
      }

      def dequotedValue: String = dequote(rawValue)
    }

    case class UnquotedWordString(rawValue: String) extends StringToken {
      override def toQuery(field: Field) = rawValue match {
        case FuzzyTermWithFuzz(term, fuzzString) => FuzzyTermQuery(field, term, Some(fuzzString.toInt))
        case FuzzyTermWithoutFuzz(term) => FuzzyTermQuery(field, term, None)
        case Prefix(prefix) => PrefixQuery(field, prefix)
        case _ => PhraseQuery(field, rawValue)
      }
    }

    case class ConcatenatedString(left: StringToken, sep: String, right: StringToken) extends StringToken {
      override def rawValue = s"${left.rawValue}${sep}${right.rawValue}"

      override def toQuery(field: Field) = rawValue match {
        case FuzzyTermWithFuzz(term, fuzzString) => FuzzyTermQuery(field, term, Some(fuzzString.toInt))
        case FuzzyTermWithoutFuzz(term) => FuzzyTermQuery(field, term, None)
        case ProximityPhrase(phrase, slopString) => ProximityQuery(field, phrase, slopString.toInt)
        case Prefix(prefix) => PrefixQuery(field, prefix)
        case _ => PhraseQuery(field, rawValue)
      }
    }
    object ConcatenatedString {
      def concatenate(sep: String): (StringToken, StringToken) => ConcatenatedString = {
        (left: StringToken, right: StringToken) => ConcatenatedString(left, sep, right)
      }
    }

    def quotedStringWithSuffix: Parser[QuotedString]
      = quotedString ~ regex("""~(\d{1,7})""".r).? ^^
      { n => QuotedString(n._1.rawValue + n._2.getOrElse("")) }

    def quotedString: Parser[QuotedString]
      = (singleQuotedString | doubleQuotedString | smartQuotedString)

    def singleQuotedString: Parser[QuotedString]
      = regex("""'((\\.)|[^'])*'""".r) ^^ { s => QuotedString(s) }

    def doubleQuotedString: Parser[QuotedString]
      = regex(""""((\\.)|[^"])*"""".r) ^^ { s => QuotedString(s) }

    def smartQuotedString: Parser[QuotedString]
      = regex("""“((\\.)|[^”])*”""".r) ^^ { s => QuotedString(s) }

    def regexString: Parser[RegexString]
      = regex("""/((\\.)|[^/])*/""".r) ^^ { s => RegexString(s) }

    def stringEvenBinaryOperatorOrParenthesis: Parser[StringToken]
      = (quotedStringWithSuffix | regexString | unquotedWordWithSuffixEvenOperator)

    def stringNotBinaryOperatorOrParenthesis: Parser[StringToken]
      = (quotedStringWithSuffix | regexString | unquotedWordWithSuffixNotOperator)

    def stringToken: Parser[StringToken]
      = chainl1(
        stringEvenBinaryOperatorOrParenthesis,
        stringNotBinaryOperatorOrParenthesis,
        // \s* -- even without whitespace, concatenate. "/foo/bar" (/foo/ is Regex) => ConcatenatedString
        (regex("\\s*".r) ^^ { sep: String => ConcatenatedString.concatenate(sep) })
      )

    def unquotedWordWithSuffixNotOperator: Parser[UnquotedWordString]
      = guard(not(binaryOperator)) ~> regex("""[^ )]+""".r) ^^
      { s => UnquotedWordString(s) }

    def unquotedWordWithSuffixEvenOperator: Parser[UnquotedWordString]
      = regex("""[^ )]+""".r) ^^
      { s => UnquotedWordString(s) }

    def quotedFieldName: Parser[String]
      = quotedString ^^
      { qs => qs.dequotedValue }

    def unquotedFieldName: Parser[String]
      = regex("""[^ ():]+""".r)

    // Match "NOT " and lookahead to "NOT(", but not "NOT*"
    def andOperator = regex("(?i)and(?=[ (])".r) ^^^ (())
    def orOperator = regex("(?i)or(?=[ (])".r) ^^^ (())
    def notOperator = regex("(?i)not(?=[ (])".r) ^^^ (())

    def parensExpression: Parser[Query] = regex("\\(\\s*".r) ~> expression <~ regex("\\s*\\)".r)
    def notExpression: Parser[NotQuery] = notOperator ~> unaryExpression ^^ NotQuery.apply _
    def binaryOperator: Parser[(Query, Query) => Query] = (
      (andOperator ^^^ { (a: Query, b: Query) => AndQuery(Vector(a, b)) }) |
      (orOperator ^^^ { (a: Query, b: Query) => OrQuery(Vector(a, b)) })
    )
  }
}
