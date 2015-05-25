package org.overviewproject.query

import org.specs2.mutable.Specification

class QueryParserSpec extends Specification {
  sequential

  def repr(node: Query): String = node match {
    case PhraseQuery(phrase) => s"[$phrase]"
    case AndQuery(node1, node2) => s"AND(${repr(node1)},${repr(node2)})"
    case OrQuery(node1, node2) => s"OR(${repr(node1)},${repr(node2)})"
    case NotQuery(node) => s"NOT(${repr(node)})"
    case FuzzyTermQuery(term, fuzziness) => s"FUZZY([$term],${fuzziness.map(_.toString).getOrElse("AUTO")})"
    case ProximityQuery(phrase, slop) => s"PROXIMITY([$phrase],${slop.toString})"
  }

  def parse(input: String): Either[SyntaxError,Query] = QueryParser.parse(input)

  def testGood(input: String, expected: String, description: String) = description in {
    parse(input).right.map(repr) must beEqualTo(Right(expected))
  }

  testGood("foo", "[foo]", "parse a term")
  testGood("foo bar", "[foo bar]", "parse space-separated terms as a phrase")
  testGood("foo AND bar", "AND([foo],[bar])", "parse AND as a boolean")
  testGood("foo OR bar", "OR([foo],[bar])", "parse OR as a boolean")
  testGood("NOT foo", "NOT([foo])", "parse NOT as a boolean")
  testGood("ANDroid ORxata NOThosaurus", "[ANDroid ORxata NOThosaurus]", "parse terms that start with operators")
  testGood("foo AND NOT bar", "AND([foo],NOT([bar]))", "give NOT precedence over AND (right-hand side)")
  testGood("NOT foo AND bar", "AND(NOT([foo]),[bar])", "give NOT precedence over AND (left-hand side)")
  testGood("'foo bar'", "[foo bar]", "parse single quotes")
  testGood("\"foo bar\"", "[foo bar]", "parse double quotes")
  testGood("“foo bar”", "[foo bar]", "parse smart quotes")
  testGood("'foo \"bar'", "[foo \"bar]", "allow double quote within single quotes")
  testGood("\"foo 'bar\"", "[foo 'bar]", "allow single quote within double quotes")
  testGood("“foo 'bar \"baz”", "[foo 'bar \"baz]", "parse single and double quotes within smart quotes")
  testGood("'foo \\'bar'", "[foo 'bar]", "allow escaping single quote with backslash")
  testGood("\"foo \\\"bar\"", "[foo \"bar]", "allow escaping double quote with backslash")
  testGood("'foo \\\\bar'", "[foo \\bar]", "allow escaping backslash")
  testGood("foo AND bar OR baz", "OR(AND([foo],[bar]),[baz])", "be left-associative (AND then OR)")
  testGood("foo OR bar AND baz", "AND(OR([foo],[bar]),[baz])", "be left-associative (OR then AND)")
  testGood("foo AND (bar OR baz)", "AND([foo],OR([bar],[baz]))", "group with parentheses")
  testGood("foo AND NOT (bar OR baz)", "AND([foo],NOT(OR([bar],[baz])))", "group with NOT and parentheses")
  testGood("(foo and bar) and not baz", "AND(AND([foo],[bar]),NOT([baz]))", "allow lowercase operators")
  testGood("('and' AND 'or') AND 'not'", "AND(AND([and],[or]),[not])", "allow quoting operators")
  testGood("foo~", "FUZZY([foo],AUTO)", "handle fuzziness")
  testGood("foo~2", "FUZZY([foo],2)", "handle fuzziness with integer")
  testGood("'foo bar'~3", "PROXIMITY([foo bar],3)", "handle proximity on quoted strings")
  testGood("NOT foo~2", "NOT(FUZZY([foo],2))", "give ~ (fuzzy) higher precedence than NOT")
  testGood("NOT 'foo bar'~2", "NOT(PROXIMITY([foo bar],2))", "give ~ (proximity) higher precedence than NOT")
}
