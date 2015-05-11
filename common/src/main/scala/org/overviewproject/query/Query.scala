package org.overviewproject.query

import play.api.libs.json.{Json,JsValue,JsString,JsNumber}

/** A way of searching for documents using a search index.
  *
  * This is modeled after ElasticSearch's (JSON) Query DSL. See
  * http://www.elastic.co/guide/en/elasticsearch/reference/1.x/query-dsl.html
  */
sealed trait Query
case class PhraseQuery(phrase: String) extends Query
case class AndQuery(node1: Query, node2: Query) extends Query
case class OrQuery(node1: Query, node2: Query) extends Query
case class NotQuery(node: Query) extends Query
case class FuzzyTermQuery(term: String, fuzziness: Option[Int]) extends Query
case class ProximityQuery(phrase: String, slop: Int) extends Query
