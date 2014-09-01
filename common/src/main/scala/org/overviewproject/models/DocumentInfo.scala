package org.overviewproject.models

/** A DocumentHeader that's as lightweight as possible. */
case class DocumentInfo(
  override val id: Long,
  override val documentSetId: Long,
  override val url: Option[String],
  override val suppliedId: String,
  override val title: String,
  override val pageNumber: Option[Int],
  override val keywords: Seq[String]
) extends DocumentHeader
