package models.export.rows

/** Specifies the (unformatted) data to export. */
trait Rows {
  /** Header columns. In a CSV, this would correspond to the first row. */
  def headers : Iterable[String]

  /** Rows of data. Each item should be a Tuple of values.
    *
    * To support CSV output, each value in each row should return something
    * legible in its toString method.
    */
  def rows : Iterable[Iterable[Any]]
}
