package org.overviewproject.query

import play.api.libs.json.{Json,JsValue,JsString,JsNumber}

/** A way of searching for documents using a search index.
  *
  * This is modeled after ElasticSearch's (JSON) Query DSL. See
  * http://www.elastic.co/guide/en/elasticsearch/reference/1.x/query-dsl.html
  */
sealed trait Query {
  def toJson: JsValue
}

case class PhraseQuery(phrase: String) extends Query {
  def toJson = Json.obj(
    "match_phrase" -> Json.obj(
      "_all" -> phrase
    )
  )
}

case class AndQuery(node1: Query, node2: Query) extends Query {
  def toJson = Json.obj(
    "bool" -> Json.obj(
      "must" -> Json.arr(node1.toJson, node2.toJson)
    )
  )
}

case class OrQuery(node1: Query, node2: Query) extends Query {
  def toJson = Json.obj(
    "bool" -> Json.obj(
      "should" -> Json.arr(node1.toJson, node2.toJson)
    )
  )
}

case class NotQuery(node: Query) extends Query {
  def toJson = Json.obj(
    "bool" -> Json.obj(
      "must_not" -> node.toJson
    )
  )
}

case class FuzzyTermQuery(term: String, fuzziness: Option[Integer] = None) extends Query {
  private def fuzzinessJsValue: JsValue = fuzziness match {
    case Some(value) => JsNumber(scala.math.BigDecimal(value))
    case None => JsString("AUTO")
  }

  def toJson = Json.obj(
    "fuzzy" -> Json.obj(
      "_all" -> Json.obj(
        "value" -> term,
        "fuzziness" -> fuzzinessJsValue
      )
    )
  )
}

case class ProximityQuery(phrase: String, slop: Integer) extends Query {
  def toJson = Json.obj(
    "match_phrase" -> Json.obj(
      "_all" -> Json.obj(
        "value" -> phrase,
        "slop" -> JsNumber(scala.math.BigDecimal(slop))
      )
    )
  )
}
