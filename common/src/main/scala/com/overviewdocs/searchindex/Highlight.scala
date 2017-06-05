package com.overviewdocs.searchindex

import play.api.libs.json.{JsArray, JsNumber}

/** A place in a document where a search query was found.
  *
  * The highlighted section is [begin,end) in UTF-16-encoded text.
  */
case class Highlight(begin: Int, end: Int)

object Highlight {
  case class Utf8Highlight(begin: Int, end: Int)
}
