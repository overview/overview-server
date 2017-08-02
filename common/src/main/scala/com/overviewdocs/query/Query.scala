package com.overviewdocs.query

import play.api.libs.json.{Json,JsValue,JsString,JsNumber}

/** A way of searching for documents using a search index.
  *
  * This is modeled after ElasticSearch's (JSON) Query DSL. See
  * http://www.elastic.co/guide/en/elasticsearch/reference/1.x/query-dsl.html
  */
sealed trait Query
sealed trait BooleanQuery extends Query
sealed trait FieldQuery extends Query {
  val field: Field
}
case object AllQuery extends Query with BooleanQuery
case class AndQuery(node1: Query, node2: Query) extends Query with BooleanQuery
case class OrQuery(node1: Query, node2: Query) extends Query with BooleanQuery
case class NotQuery(node: Query) extends Query with BooleanQuery
case class PhraseQuery(field: Field, phrase: String) extends Query with FieldQuery
case class PrefixQuery(field: Field, prefix: String) extends Query with FieldQuery
case class FuzzyTermQuery(field: Field, term: String, fuzziness: Option[Int]) extends Query with FieldQuery
case class ProximityQuery(field: Field, phrase: String, slop: Int) extends Query with FieldQuery

/** A regular-expression query.
  *
  * The regex hasn't actually been parsed: that will happen during execution and
  * cause a warning (and match no documents) if it's invalid. (Rationale: the
  * query language doesn't have any invalid input.)
  */
case class RegexQuery(field: Field, regex: String) extends Query with FieldQuery

object Query {
  def walkFields(query: Query)(f: Field => Unit): Unit = query match {
    case AllQuery => {}
    case AndQuery(p1, p2) => {
      walkFields(p1)(f)
      walkFields(p2)(f)
    }
    case OrQuery(p1, p2) => {
      walkFields(p1)(f)
      walkFields(p2)(f)
    }
    case NotQuery(p) => walkFields(p)(f)
    case fq: FieldQuery => f(fq.field)
  }
}
