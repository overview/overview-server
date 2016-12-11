package com.overviewdocs.models

import java.util.Date // should be java.time.LocalDateTime
import play.api.libs.json.JsObject

import com.overviewdocs.models.DocumentDisplayMethod.DocumentDisplayMethod


/** Metadata about a Document.
  */
trait DocumentHeader {
  val id: Long
  val documentSetId: Long
  val url: Option[String]
  val suppliedId: String
  val title: String
  val pageNumber: Option[Int]
  val keywords: Seq[String]
  val createdAt: Date
  val displayMethod: DocumentDisplayMethod
  val isFromOcr: Boolean
  val metadataJson: JsObject
  val thumbnailLocation: Option[String]
  val text: String

  def viewUrl: Option[String] = url

  /** Returns NFKC-normalized tokens split according to Unicode tr29. */
  def tokens: Seq[String] = Seq()
}
