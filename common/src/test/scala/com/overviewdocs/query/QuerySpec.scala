package com.overviewdocs.query

import org.specs2.mutable.Specification

class QuerySpec extends Specification {
  def repr(node: Query): String = node match {
    case AllQuery => "ALL"
    case AndQuery(nodes) => s"AND(${nodes.map(n => repr(n)).mkString(",")})"
    case OrQuery(nodes) => s"OR(${nodes.map(n => repr(n)).mkString(",")})"
    case NotQuery(node) => s"NOT(${repr(node)})"
    case PhraseQuery(_, phrase) => s"$phrase"
    case PrefixQuery(_, phrase) => s"PREF($phrase)"
    case FuzzyTermQuery(_, term, fuzziness) => s"FUZZ($term,${fuzziness.fold("AUTO")(_.toString)})"
    case ProximityQuery(_, phrase, slop) => s"PROX($phrase,${slop.toString})"
    case RegexQuery(_, regex) => s"REGEX($regex)"
  }

  private implicit def stringToQuery(s: String): Query = PhraseQuery(Field.All, s)
  private def AND(queries: Query*) = AndQuery(Vector(queries: _*))
  private def OR(queries: Query*) = OrQuery(Vector(queries: _*))
  private def NOT(query: Query) = NotQuery(query)
  private def flatten(q: Query): String = repr(q.flatten)

  "Query" should {
    "flatten" should {
      "handle left-leaning AND" in {
        flatten(AND(AND("a", "b"), "c")) must beEqualTo("AND(a,b,c)")
      }

      "handle right-leaning AND" in {
        flatten(AND("a", AND("b", "c"))) must beEqualTo("AND(a,b,c)")
      }

      "recurse" in {
        flatten(AND(AND(AND(AND(AND("a", "b"), "c"), "d"), "e"), "f")) must beEqualTo("AND(a,b,c,d,e,f)")
      }

      "not flatten OR within AND" in {
        flatten(AND(AND(AND(OR(AND("a", "b"), "c"), "d"), "e"), "f")) must beEqualTo("AND(OR(AND(a,b),c),d,e,f)")
      }

      "not flatten away NOT" in {
        flatten(AND(AND(NOT(AND(AND(AND("a", "b"), "c"), "d")), "e"), "f")) must beEqualTo("AND(NOT(AND(a,b,c,d)),e,f)")
      }

      "flatten OR" in {
        flatten(OR(OR(OR(OR(OR("a", "b"), "c"), "d"), "e"), "f")) must beEqualTo("OR(a,b,c,d,e,f)")
      }
    }
  }
}
