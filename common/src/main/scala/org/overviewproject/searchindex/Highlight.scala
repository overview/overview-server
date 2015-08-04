package com.overviewdocs.searchindex

/** A place in a document where a search query was found.
  *
  * The highlighted section is [begin,end).
  */
case class Highlight(begin: Int, end: Int)
