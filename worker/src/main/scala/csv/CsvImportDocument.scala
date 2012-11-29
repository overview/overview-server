/*
 * CsvImportDocument.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package csv

import persistence.PersistentCsvImportDocument

/** Document generated from a CsvImport. suppliedId is present if an "id" column exists in the source */
class CsvImportDocument(val text: String,
    val suppliedId: Option[String] = None, 
    val url: Option[String] = None) extends PersistentCsvImportDocument 