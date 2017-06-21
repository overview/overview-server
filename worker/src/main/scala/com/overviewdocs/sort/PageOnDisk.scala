package com.overviewdocs.sort

import akka.stream.scaladsl.Source
import java.nio.file.Path

/** A page of Records all written to disk.
  *
  * On disk, the Records take a bit less space than in memory. The file format
  * is simply a sequence of
  * `(id[4], canonicalPosition[4], nBytes[4], collationKey[nBytes])`
  */
case class PageOnDisk(
  nRecords: Int,
  path: Path
) {
  def toSource: Source[Record, _] = ???
}
