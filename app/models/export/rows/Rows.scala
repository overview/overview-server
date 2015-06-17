package models.export.rows

import play.api.libs.iteratee.Enumerator

/** Specifies the (unformatted) data to export. */
case class Rows(
  /** Header columns. In a CSV, this would correspond to the first row. */
  headers: Array[String],

  /** Rows of data. */
  rows: Enumerator[Array[String]]
)
