package org.overviewproject.documentcloud

/** Information about a document stored by DocumentCloud */
case class Document(id: String, title: String, pages: Int, access: String, textUrl: String, pageUrlTemplate: String) {
  val url: String = textUrl
  val pageNumber: Option[Int] = None
}
