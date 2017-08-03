package com.overviewdocs.query

import scala.collection.immutable

/** A way of searching for documents using a search index.
  *
  * This is modeled after ElasticSearch's (JSON) Query DSL. See
  * http://www.elastic.co/guide/en/elasticsearch/reference/1.x/query-dsl.html
  */
sealed trait Query {
  /** Returns an equivalent Query, with AndQuery(AndQuery(a, b), c) rewritten
    * to AndQuery(a, b, c).
    */
  def flatten: Query
}

sealed trait BooleanQuery extends Query

case class AndQuery(nodes: immutable.Seq[Query]) extends Query with BooleanQuery {
  override def flatten = AndQuery(nodes.flatMap(_ match {
    case AndQuery(children) => {
      // recurse
      children.flatMap(_.flatten match {
        case AndQuery(subChildren) => subChildren
        case query => Vector(query)
      })
    }
    case query => Vector(query)
  }))
}

case class OrQuery(nodes: immutable.Seq[Query]) extends Query with BooleanQuery {
  override def flatten = OrQuery(nodes.flatMap(_ match {
    case OrQuery(children) => {
      // recurse
      children.flatMap(_.flatten match {
        case OrQuery(subChildren) => subChildren
        case query => Vector(query)
      })
    }
    case query => Vector(query)
  }))
}

case class NotQuery(node: Query) extends Query with BooleanQuery {
  override def flatten = NotQuery(node.flatten)
}

case object AllQuery extends Query with BooleanQuery {
  override def flatten = this
}

sealed trait FieldQuery extends Query {
  val field: Field
  override def flatten = this
}
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
    case AndQuery(nodes) => { nodes.foreach(q => walkFields(q)(f)) }
    case OrQuery(nodes) => { nodes.foreach(q => walkFields(q)(f)) }
    case NotQuery(p) => walkFields(p)(f)
    case fq: FieldQuery => f(fq.field)
  }
}
