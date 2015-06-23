/*
 * CsvImportDocument.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import play.api.libs.json.JsObject

import org.overviewproject.models.{Document,DocumentDisplayMethod}

/** Document generated from a CsvImport. suppliedId is present if an "id" column exists in the source */
class CsvImportDocument(
  val text: String,
  val suppliedId: Option[String] = None,
  val url: Option[String] = None,
  val title: Option[String] = None,
  val tags: Set[String] = Set()
) {
  
  
  def toDocument(id: Long, documentSetId: Long): Document = Document(
    id,
    documentSetId,
    url,
    suppliedId.getOrElse(""),
    title.getOrElse(""),
    None,
    Seq(),
    new java.util.Date(),
    None,
    None,
    DocumentDisplayMethod.auto,
    JsObject(Seq()),
    text
  )
}
