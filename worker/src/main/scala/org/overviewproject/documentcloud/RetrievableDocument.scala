package org.overviewproject.documentcloud

/** The information necessary to retrieve a document from document cloud */
trait RetrievableDocument {
  /** The DocumentCloud ID */
  val id: String

  val title: String

  /** The url used to return the text of the document */
  val url: String
}

/** Retrieval information for the complete document */
case class CompleteDocument(info: Document) extends RetrievableDocument {
  override val id: String = info.id
  override val title: String = info.title

  override val url: String = s"https://www.documentcloud.org/api/documents/${info.id}.txt"
}

case class DocumentPage(info: Document, pageNum: Int) extends RetrievableDocument {
  private val PageHolder = "{page}"

  override val id: String = s"${info.id}#p$pageNum"
  override val title: String = s"${info.title} p.$pageNum" 
  override val url: String = info.pageUrlTemplate.replace(PageHolder, s"$pageNum")
}