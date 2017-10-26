package com.overviewdocs.query

import org.specs2.mutable.Specification

class QueryParserSpec extends Specification {
  def repr(field: Field): String = field match {
    case Field.All => ""
    case Field.Notes => "notes:"
    case Field.Title => "title:"
    case Field.Text => "text:"
    case Field.Metadata(name) => s"META(${name}):"
  }

  def repr(node: Query): String = node match {
    case AllQuery => "ALL"
    case AndQuery(nodes) => s"AND(${nodes.map(n => repr(n)).mkString(",")})"
    case OrQuery(nodes) => s"OR(${nodes.map(n => repr(n)).mkString(",")})"
    case NotQuery(node) => s"NOT(${repr(node)})"
    case PhraseQuery(field, phrase) => s"${repr(field)}[$phrase]"
    case PrefixQuery(field, phrase) => s"${repr(field)}PREF([$phrase])"
    case FuzzyTermQuery(field, term, fuzziness) => s"${repr(field)}FUZZ([$term],${fuzziness.fold("AUTO")(_.toString)})"
    case ProximityQuery(field, phrase, slop) => s"${repr(field)}PROX([$phrase],${slop.toString})"
    case RegexQuery(field, regex) => s"${repr(field)}REGEX([$regex])"
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
  testGood("foo NOT bar", "[foo NOT bar]", "make sure NOT is unary, not binary")
  testGood("AND foo", "[AND foo]", "make sure AND is binary, not unary")
  testGood("OR foo", "[OR foo]", "make sure OR is binary, not unary")
  testGood("ANDroid ORxata NOThosaurus", "[ANDroid ORxata NOThosaurus]", "parse terms that start with operators")
  testGood("foo AND NOT bar", "AND([foo],NOT([bar]))", "give NOT precedence over AND (right-hand side)")
  testGood("NOT foo AND bar", "AND(NOT([foo]),[bar])", "give NOT precedence over AND (left-hand side)")
  testGood("foo AND bar AND baz", "AND([foo],[bar],[baz])", "flatten queries")
  testGood("'foo bar'", "[foo bar]", "parse single quotes")
  testGood("\"foo bar\"", "[foo bar]", "parse double quotes")
  testGood("“foo bar”", "[foo bar]", "parse smart quotes")
  testGood("'foo \"bar'", "[foo \"bar]", "allow double quote within single quotes")
  testGood("\"foo 'bar\"", "[foo 'bar]", "allow single quote within double quotes")
  testGood("“foo 'bar \"baz”", "[foo 'bar \"baz]", "parse single and double quotes within smart quotes")
  testGood("'foo \\'bar'", "[foo 'bar]", "allow escaping single quote with backslash")
  testGood("\"foo \\\"bar\"", "[foo \"bar]", "allow escaping double quote with backslash")
  testGood("'foo \\\\bar'", "[foo \\bar]", "allow escaping backslash")
  testGood("\"foo", "[\"foo]", "parse never-ending quotes as a single term")
  testGood("foo AND bar OR baz", "OR(AND([foo],[bar]),[baz])", "be left-associative (AND then OR)")
  testGood("foo OR bar AND baz", "AND(OR([foo],[bar]),[baz])", "be left-associative (OR then AND)")
  testGood("foo AND (bar OR baz)", "AND([foo],OR([bar],[baz]))", "group with parentheses")
  testGood("foo AND NOT (bar OR baz)", "AND([foo],NOT(OR([bar],[baz])))", "group with NOT and parentheses")
  testGood("foo and bar and not baz", "AND([foo],[bar],NOT([baz]))", "allow lowercase operators")
  testGood("foo AND (bar OR", "AND([foo],[(bar OR])", "treat un-closed paren clause as a String token")
  //TODO: figure out desired behavior
  //testGood("(foo AND )", "[(foo AND )]", "not parse incomplete parens")
  //testGood("(foo AND bar))", "AND([foo],[bar)])", "treat extra closing paren as a String token")
  testGood("'and' AND 'or' AND 'not'", "AND([and],[or],[not])", "allow quoting operators")
  testGood("foo~", "FUZZ([foo],AUTO)", "handle fuzziness")
  testGood("foo~2", "FUZZ([foo],2)", "handle fuzziness with integer")
  testGood("'foo bar'~3", "PROX([foo bar],3)", "handle proximity on quoted strings")
  testGood("NOT foo~2", "NOT(FUZZ([foo],2))", "give ~ (fuzzy) higher precedence than NOT")
  testGood("NOT 'foo bar'~2", "NOT(PROX([foo bar],2))", "give ~ (proximity) higher precedence than NOT")
  testGood("notes:foo bar", "notes:[foo bar]", "specify the notes field")
  testGood("title:foo bar", "title:[foo bar]", "specify the title field")
  testGood("text:foo bar", "text:[foo bar]", "specify the text field")
  testGood("text: foo bar", "text:[foo bar]", "nix spaces after field name")
  testGood("NOT title:foo bar AND bar", "AND(NOT(title:[foo bar]),[bar])", "give field higher precedence than NOT")
  testGood("title:foo~", "title:FUZZ([foo],AUTO)", "allow field on fuzzy query")
  testGood("title:foo bar~2", "title:PROX([foo bar],2)", "allow field on proximity query")
  testGood("foo bar*", "PREF([foo bar])", "allow prefix query")
  testGood("*", "[*]", "not allow zero-character prefix")
  testGood("f*", "[f*]", "now allow one-character prefix")
  testGood("fo*", "PREF([fo])", "allow two-character prefix")
  testGood("title:/path/subpath/*", "title:PREF([/path/subpath/])", "allow field+prefix query")
  testGood("foo* bar", "[foo* bar]", "ignore prefix operator in the middle of a phrase")
  testGood("NOT*", "PREF([NOT])", "parse NOT* as a phrase")
  testGood("NOT fo*", "NOT(PREF([fo]))", "parse NOT x* as one would expect")
  testGood("foo:bar*", "META(foo):PREF([bar])", "parse metadata field name")
  testGood("'Foo Bar':baz", "META(Foo Bar):[baz]", "parse quoted metadata field name")
  testGood("foo:bar AND bar:baz", "AND(META(foo):[bar],META(bar):[baz])", "watch for operators in metadata field text")
  testGood("foo:bar bar:baz", "META(foo):[bar bar:baz]", "do not infer operators where there are none")
  testGood("NOT:blah", "META(NOT):[blah]", "allow operators as metadata field names")
  testGood("text:/foo/", "text:REGEX([foo])", "parse a regex node")
  testGood("text: /foo/", "text:REGEX([foo])", "allow whitespace before a regex")
  testGood("text:/foo bar baz/", "text:REGEX([foo bar baz])", "allow spaces in regexes")
  testGood("text:/foo AND bar/", "text:REGEX([foo AND bar])", "allow conjunctions in regexes")
  testGood("text:/foo/ AND bar", "AND(text:REGEX([foo]),[bar])", "allow conjunctions after regexes")
  testGood("text:/foo", "text:[/foo]", "parse a missing slash as text, not regex")
  testGood("text:/foo/ bar", "text:[/foo/ bar]", "parse a supposed regex and space and text as one big text")
  testGood("text:/foo/ AND bar", "AND(text:REGEX([foo]),[bar])", "allow AND after a regex")
  testGood("text:/foo/ NOT bar", "text:[/foo/ NOT bar]", "not allow NOT after a regex")
  // We pass-through backslashes: the only escape sequence we care about is "\/"
  // and re2 will parse that just fine. The passed-through backslashes help us
  // craft more accurate warnings.
  testGood("text:/foo\\/bar/", "text:REGEX([foo\\/bar])", "allow backslash-escaping slashes")
  testGood("text:/foo\\\\bar/", "text:REGEX([foo\\\\bar])", "allow backslash-escaping backslashes")
  testGood("text:/\\w\\d\\b/", "text:REGEX([\\w\\d\\b])", "allow backslash-escaping typical escape sequences")
  testGood("text:/foo/bar/", "text:[/foo/bar/]", "not allow inner slashes in regexes")
  testGood("text:/foo\\\\/bar/", "text:[/foo\\\\/bar/]", "not escape backslashes when not in a regex")

  // Test our documentation
  testGood("John Smith", "[John Smith]", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [1]")
  testGood("Pizza~", "FUZZ([Pizza],AUTO)", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [2]")
  testGood("John Smith AND Alice Smith", "AND([John Smith],[Alice Smith])", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [3]")
  testGood("John Smith OR Alice Smith", "OR([John Smith],[Alice Smith])", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [4]")
  testGood("John Smith AND NOT Alice Smith", "AND([John Smith],NOT([Alice Smith]))", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [5]")
  testGood("Alice AND NOT (Bob OR Carol)", "AND([Alice],NOT(OR([Bob],[Carol])))", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [6]")
  testGood(""""John and Alice Smith"""", "[John and Alice Smith]", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [7]")
  testGood("John Smith~2", "PROX([John Smith],2)", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [8]")
  testGood("Smith*", "PREF([Smith])", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [9]")
  testGood("title:John Smith", "title:[John Smith]", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [10]")
  testGood("text:John Smith", "text:[John Smith]", "https://blog.overviewdocs.com/2015/05/29/overviews-search-syntax/ [11]")

  testGood("Date:2015-11-08", "META(Date):[2015-11-08]", "https://blog.overviewdocs.com/2017/06/21/search-in-your-fields/ [1]")
  testGood("date:2015-11-08", "META(date):[2015-11-08]", "https://blog.overviewdocs.com/2017/06/21/search-in-your-fields/ [2]")
  testGood(""""Full Name":John Smith""", "META(Full Name):[John Smith]", "https://blog.overviewdocs.com/2017/06/21/search-in-your-fields/ [3]")
  testGood("Author:Adam H*", "META(Author):PREF([Adam H])", "https://blog.overviewdocs.com/2017/06/21/search-in-your-fields/ [4]")
  testGood("text:Overview", "text:[Overview]", "https://blog.overviewdocs.com/2017/06/21/search-in-your-fields/ [5]")
  testGood("title:Overview", "title:[Overview]", "https://blog.overviewdocs.com/2017/06/21/search-in-your-fields/ [6]")
  testGood(""""text":Overview""", "META(text):[Overview]", "https://blog.overviewdocs.com/2017/06/21/search-in-your-fields/ [7]")
}
