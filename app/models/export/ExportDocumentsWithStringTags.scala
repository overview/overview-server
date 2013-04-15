package models.export

import au.com.bytecode.opencsv.CSVWriter
import java.io.{ BufferedWriter, OutputStream, OutputStreamWriter }

import models.OverviewDatabase
import models.orm.finders.FinderResult
import org.overviewproject.tree.orm.Document

class ExportDocumentsWithStringTags(documents: FinderResult[(Document,Option[String])]) extends Export {
  override def contentTypeHeader = "text/csv; charset=\"utf-8\""

  override def exportTo(outputStream: OutputStream) = {
    val writer = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"))
    val csvWriter = new CSVWriter(writer)
    csvWriter.writeNext(Array("id", "text", "url", "tags"))
    OverviewDatabase.inTransaction {
      documents.foreach(Function.tupled { (document: Document, tags: Option[String]) =>
        csvWriter.writeNext(Array(
          document.suppliedId.getOrElse(""),
          document.text.getOrElse(""),
          document.url.getOrElse(""),
          tags.getOrElse("")
        ))
      })
    }
    csvWriter.close
  }
}
