package com.overviewdocs.jobhandler.filegroup.task

import java.time.Instant
import play.api.libs.json.JsObject

import com.overviewdocs.models.{Document,DocumentDisplayMethod,PdfNoteCollection}

/** Something we'll pass to WriteDocuments. */
case class IncompleteDocument(
  val filename: String,
  val pageNumber: Option[Int],
  val thumbnailLocation: Option[String],
  val fileId: Option[Long],
  val pageId: Option[Long],
  val displayMethod: DocumentDisplayMethod.Value,
  val isFromOcr: Boolean,
  val text: String,
  val createdAt: Instant
) {
  def toDocument(documentSetId: Long, id: Long, metadataJson: JsObject): Document = Document(
    id=id,
    documentSetId=documentSetId,
    thumbnailLocation=thumbnailLocation,
    url=None,
    suppliedId=filename,
    title=filename,
    pageNumber=pageNumber,
    keywords=Seq(),
    createdAt=new java.util.Date(createdAt.toEpochMilli),
    fileId=fileId,
    pageId=pageId,
    displayMethod=displayMethod,
    isFromOcr=isFromOcr,
    metadataJson=metadataJson,
    pdfNotes=PdfNoteCollection(Array()),
    text=text
  )
}
