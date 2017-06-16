package com.overviewdocs.searchindex

import com.overviewdocs.query.FieldInSearchIndex

/** Reasons for the search results to be inaccurate.
  *
  * The searchindex module may refuse to run an infinitely-slow query, instead
  * running a faster query and returning approximate results. These warnings
  * describe all the ways in which the results might be wrong.
  */
sealed trait SearchWarning
object SearchWarning {
  /** The user searched a term that expands -- "a*", for instance -- and there
    * were too many expansions.
    *
    * The search went ahead, but only with <tt>nExpansions</tt> terms instead of
    * all possible ones.
    */
  case class TooManyExpansions(
    /** The field the user searched */
    searchField: FieldInSearchIndex,

    /** The word the user searched for that we truncated. For example: "a*" */
    searchTerm: String,

    /** The number of expansions we actually searched for.
      *
      * There is no way to know how many expansions were _excluded_.
      */
    nExpansions: Int
  ) extends SearchWarning

  /** The user ran a fuzzy query with too much fuzz, so we lowered the amount
    * of fuzz before running the search.
    *
    * Lucene's fuzzy queries max out at fuzz=2, and that's hard-coded.
    */
  case class TooMuchFuzz(
    /** The field the user searched */
    searchField: FieldInSearchIndex,

    /** The term the user searched for. For example: "a~43" */
    searchTerm: String,

    /** The amount of fuzz we used (which is the maximum amount of fuzz). */
    allowedFuzz: Int
  ) extends SearchWarning

  /** The document set isn't indexed yet. */
  case object IndexDoesNotExist extends SearchWarning
}
