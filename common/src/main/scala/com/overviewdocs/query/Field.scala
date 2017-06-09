package com.overviewdocs.query

/** Field a user can search in. */
sealed trait Field

/** Field in the actual search index. */
sealed trait FieldInSearchIndex

object Field {
  case object All extends Field
  case object Text extends Field with FieldInSearchIndex
  case object Title extends Field with FieldInSearchIndex
}
