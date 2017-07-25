package com.overviewdocs.searchindex

import com.overviewdocs.models.DocumentIdSet

/** A response to a search for IDs from the searchindex.
  *
  * This assumes all document IDs are generated by ANDing a 32-bit document-set
  * ID with a 32-bit document _number_ within the document set. (We'll call that
  * number a "lower ID" in this class.)
  *
  * It's small enough to send over the wire. A 10M-document docset will have a
  * 10M-element bitset, which is 1.25MB.
  */
case class SearchResult(
  /** Document IDs found by the search. */
  val documentIds: DocumentIdSet,

  /** Reasons documentIds might _not_ be what the user searched for.
    *
    * We may replace an infinitely-slow query with a faster-but-less-accurate
    * one. Warnings describe how the results might be wrong.
    */
  val warnings: List[SearchWarning]
)