/*
 * CsvImportDocument.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import play.api.libs.json.{JsObject,JsString}

import org.overviewproject.models.{Document,DocumentDisplayMethod}

/** Document generated from a CsvImport. suppliedId is present if an "id" column exists in the source */
case class CsvImportDocument(
  val text: String,
  val suppliedId: String,
  val url: Option[String],
  val title: String,
  val tags: Set[String],
  val metadata: Map[String,String]
) {
  def toDocument(id: Long, documentSetId: Long): Document = Document(
    id,
    documentSetId,
    url,
    suppliedId,
    title,
    None,
    Seq(),
    new java.util.Date(),
    None,
    None,
    DocumentDisplayMethod.auto,
    JsObject(metadata.mapValues(JsString).toSeq),
    text
  )
}
