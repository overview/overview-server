package models.export

import java.io.{ BufferedWriter, OutputStream, OutputStreamWriter }

import au.com.bytecode.opencsv.CSVWriter
import org.overviewproject.tree.orm.{Document,Tag}
import org.overviewproject.tree.orm.finders.FinderResult

import models.{ OverviewDatabase, OverviewDocument }

class ExportDocumentsWithColumnTags(
  documents: FinderResult[(Document,Option[String])],
  tags: FinderResult[Tag])
  extends Export {

  private val FirstTagColumn : Int = 3

  private def writeHeaders(tags: Iterable[Tag])(implicit writer: CSVWriter, scratch: Array[String]) : Unit = {
    scratch(0) = "id"
    scratch(1) = "text"
    scratch(2) = "url"

    var i = FirstTagColumn
    tags.foreach { tag: Tag =>
      scratch(i) = tag.name
      i += 1
    }

    writer.writeNext(scratch)
  }

  private def writeDocument(document: OverviewDocument, tagsString: Option[String], tagIds: Iterable[Long])(implicit writer: CSVWriter, scratch: Array[String]) : Unit = {
    val tags = tagsString.getOrElse("")
      .split(',')
      .collect {
        case s: String if s.length > 0 => s.toLong
      }
      .toSet

    scratch(0) = document.suppliedId.getOrElse("")
    scratch(1) = document.text.getOrElse("")
    scratch(2) = document.url.getOrElse("")

    var i = FirstTagColumn
    tagIds.foreach { id: Long =>
      scratch(i) = if (tags.contains(id)) "1" else ""
      i += 1
    }

    writer.writeNext(scratch)
  }

  override def contentTypeHeader = "text/csv; charset=\"utf-8\""

  override def exportTo(outputStream: OutputStream) : Unit = {
    writeUtf8Bom(outputStream)

    val writer = new BufferedWriter(new OutputStreamWriter(outputStream, "utf-8"))
    implicit val csvWriter = new CSVWriter(writer)

    OverviewDatabase.inTransaction {
      implicit val scratch = new Array[String](3 + tags.size)

      writeHeaders(tags)

      val tagIds = tags.map(_.id)

      documents.foreach(Function.tupled { (ormDocument: Document, tagsString: Option[String]) =>
        val document = OverviewDocument(ormDocument)
        writeDocument(document, tagsString, tagIds)
      })
    }
    csvWriter.close
  }
}
