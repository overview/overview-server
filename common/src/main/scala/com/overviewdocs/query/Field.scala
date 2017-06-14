package com.overviewdocs.query

/** Field a user can search.
  *
  * This includes all actual fields, plus an "all" pseudo-field that searches
  * the text and title fields.
  */
sealed trait Field

/** Field a user can search in the search index.
  *
  * This field may or may not actually _exist_ on disk somewhere. By "in search
  * index", we mean: "the search index should look in exactly this field and
  * return no documents if the field does not exist."
  */
sealed trait FieldInSearchIndex

object Field {
  case object All extends Field
  case object Notes extends Field with FieldInSearchIndex
  case object Text extends Field with FieldInSearchIndex
  case object Title extends Field with FieldInSearchIndex
  case class Metadata(fieldName: String) extends Field with FieldInSearchIndex
}
