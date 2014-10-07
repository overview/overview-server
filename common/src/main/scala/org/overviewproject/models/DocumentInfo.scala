package org.overviewproject.models

/** A DocumentHeader that's as lightweight as possible.
  *
  * The main difference between this and a full-fledged Document: its "text" is
  * _always_ the empty String. That makes a DocumentInfo quite small, while a
  * Document is quite large.
  */
case class DocumentInfo(
  override val id: Long,
  override val documentSetId: Long,
  override val url: Option[String],
  override val suppliedId: String,
  override val title: String,
  override val pageNumber: Option[Int],
  override val keywords: Seq[String]
) extends DocumentHeader {
  override val text = ""
}
