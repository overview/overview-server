package models.export

import java.io.{ BufferedWriter, OutputStream, OutputStreamWriter }

import au.com.bytecode.opencsv.CSVWriter
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.FinderResult

import models.{ OverviewDatabase, OverviewDocument }

class ExportDocumentsWithStringTags(documents: FinderResult[(Document,Option[String])]) extends Export {
  override def contentTypeHeader = "text/csv; charset=\"utf-8\""

  override def exportTo(outputStream: OutputStream) = {
    writeUtf8Bom(outputStream)

    val writer = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"))
    val csvWriter = new CSVWriter(writer)

    csvWriter.writeNext(Array("id", "text", "url", "tags"))

    OverviewDatabase.inTransaction {
      documents.foreach(Function.tupled { (ormDocument: Document, tags: Option[String]) =>
        val document = OverviewDocument(ormDocument)
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
