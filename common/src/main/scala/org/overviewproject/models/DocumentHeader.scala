package org.overviewproject.models

/** Metadata about a Document.
  *
  * The main difference between this and a full-fledged Document: this is
  * rather small, while a Document is rather large. (There is no "text" field
  * here.)
  */
trait DocumentHeader {
  val id: Long
  val documentSetId: Long
  val url: Option[String]
  val suppliedId: String
  val title: String
  val pageNumber: Option[Int]
  val keywords: Seq[String]
}
