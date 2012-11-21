package csv

import persistence.PersistentCsvImportDocument

class CsvImportDocument(val text: String, val suppliedId: Option[String] = None) extends PersistentCsvImportDocument 