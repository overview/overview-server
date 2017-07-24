package com.overviewdocs.csv

import play.api.libs.json.{JsObject,JsString}

import com.overviewdocs.models.{Document,DocumentDisplayMethod,PdfNoteCollection}

case class CsvDocument(
  suppliedId: String,
  url: String,
  title: String,
  tags: Set[String],
  text: String,
  metadata: Seq[(String,String)]
) {
  def toDocument(id: Long, documentSetId: Long): Document = Document(
    id,
    documentSetId,
    Some(url),
    suppliedId,
    title,
    None,
    new java.util.Date(),
    None,
    None,
    DocumentDisplayMethod.auto,
    false,
    JsObject(metadata.map(t => t._1 -> JsString(t._2))),
    None,
    PdfNoteCollection(Array()),
    text
  )
}
