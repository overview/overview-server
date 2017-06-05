package com.overviewdocs.searchindex

import play.api.libs.json.{JsArray, JsNumber}

/** A place in a document where a search query was found, plus context.
  *
  * Each snippet points to characters [start,end) in UTF-16-encoded text.
  */
case class Snippet(begin: Int, end: Int, highlights: Seq[Highlight])
