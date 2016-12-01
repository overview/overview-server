package com.overviewdocs.searchindex

import play.api.libs.json.{JsArray, JsNumber}

/** A place in a document where a search query was found.
  *
  * The highlighted section is [begin,end).
  */
case class Highlight(begin: Int, end: Int)

object Highlight {

  def asJson(highlights: Seq[Highlight]): JsArray =
    JsArray(highlights.map { highlight =>
      JsArray(Seq(JsNumber(highlight.begin), JsNumber(highlight.end)))
    })

}
