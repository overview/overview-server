package models.export

import java.io.OutputStream
import org.overviewproject.tree.orm.{Document,Tag}
import org.overviewproject.tree.orm.finders.FinderResult

import models.OverviewDatabase

class ExportDocumentsWithColumnTags(
  documents: FinderResult[(Document,Option[String])],
  tags: FinderResult[Tag])
  extends Export {

  override def contentTypeHeader = "text/csv; charset=\"utf-8\""

  override def exportTo(outputStream: OutputStream) = {
    OverviewDatabase.inTransaction {
      val rows = new models.export.rows.DocumentsWithColumnTags(documents, tags)
      models.export.format.CsvFormat.writeContentsToOutputStream(rows, outputStream)
    }
  }
}
