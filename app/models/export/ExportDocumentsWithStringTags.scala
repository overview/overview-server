package models.export

import java.io.OutputStream
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.FinderResult

import models.OverviewDatabase

class ExportDocumentsWithStringTags(documents: FinderResult[(Document,Option[String])]) extends Export {
  override def contentTypeHeader = "text/csv; charset=\"utf-8\""

  override def exportTo(outputStream: OutputStream) = {
    OverviewDatabase.inTransaction {
      val rows = new models.export.rows.DocumentsWithStringTags(documents)
      models.export.format.CsvFormat.writeContentsToOutputStream(rows, outputStream)
    }
  }
}
