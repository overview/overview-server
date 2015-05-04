package org.overviewproject.query

import play.api.libs.json.{Json,JsValue,JsString,JsNumber}

sealed trait AstNode {
  def toJson: JsValue
}

case class PhraseNode(phrase: String) extends AstNode {
  def toJson = Json.obj(
    "match_phrase" -> Json.obj(
      "_all" -> phrase
    )
  )
}

case class AndNode(node1: AstNode, node2: AstNode) extends AstNode {
  def toJson = Json.obj(
    "bool" -> Json.obj(
      "must" -> Json.arr(node1.toJson, node2.toJson)
    )
  )
}

case class OrNode(node1: AstNode, node2: AstNode) extends AstNode {
  def toJson = Json.obj(
    "bool" -> Json.obj(
      "should" -> Json.arr(node1.toJson, node2.toJson)
    )
  )
}

case class NotNode(node: AstNode) extends AstNode {
  def toJson = Json.obj(
    "bool" -> Json.obj(
      "must_not" -> node.toJson
    )
  )
}

case class FuzzyTermNode(term: String, fuzziness: Option[Integer] = None) extends AstNode {
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

case class ProximityNode(phrase: String, slop: Integer) extends AstNode {
  def toJson = Json.obj(
    "match_phrase" -> Json.obj(
      "_all" -> Json.obj(
        "value" -> phrase,
        "slop" -> JsNumber(scala.math.BigDecimal(slop))
      )
    )
  )
}
