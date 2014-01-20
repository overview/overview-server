package org.overviewproject.jobhandler.filegroup

import java.io.InputStream
import scala.util.control.Exception.ultimately
import akka.actor.Actor
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.util.PDFTextStripper
import org.overviewproject.database.Database
import org.overviewproject.database.orm.FileText
import org.overviewproject.database.orm.finders.GroupedFileUploadFinder
import org.overviewproject.database.orm.stores.GroupedProcessedFileStore
import org.overviewproject.database.orm.Schema
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.GroupedProcessedFile
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.util.Textify

object TextExtractorProtocol {
  case class ExtractText(fileGroupId: Long, uploadedFileId: Long)
}

trait TextExtractorComponents {

  val dataStore: DataStore
  val pdfProcessor: PdfProcessor

  trait DataStore {
    def findFileUpload(fileUploadId: Long): Option[GroupedFileUpload]
    def fileContentStream(oid: Long): InputStream
    def storeFile(file: GroupedProcessedFile): Unit
    def storeText(fileId: Long, text: String): Unit
  }

  trait PdfProcessor {
    def extractText(inputStream: InputStream): String
  }
}

class TextExtractor extends Actor {
  self: TextExtractorComponents =>

  import TextExtractorProtocol._

  def receive = {
    case ExtractText(fileGroupId, fileUploadId) => {
      dataStore.findFileUpload(fileUploadId).map { fileUpload =>
        val inputStream = dataStore.fileContentStream(fileUpload.contentsOid)
        val text = pdfProcessor.extractText(inputStream)
        val file = GroupedProcessedFile(
          fileGroupId,
          fileUpload.contentType,
          fileUpload.name,
          None,
          Some(text),
          fileUpload.contentsOid,
          fileUpload.size)
        dataStore.storeFile(file)
      }
      sender ! JobDone(fileGroupId)
      context.stop(self)
    }
  }
}

trait PdfBoxPdfProcessor {
  private val pdfTextStripper = new PDFTextStripper

  /** See org.overviewproject.util.Textify */
  protected def textify(s: String) : String

  protected def inputStreamToRawText(inputStream: InputStream) : String = {
    val document = PDDocument.load(inputStream)
    ultimately(document.close) {
      pdfTextStripper.getText(document)
    }
  }

  def extractText(inputStream: InputStream): String = {
    val rawText = inputStreamToRawText(inputStream)
    textify(rawText)
  }

}

class TextExtractorImpl extends TextExtractor with TextExtractorComponents {
  class DataStoreImpl extends DataStore {
    override def findFileUpload(fileUploadId: Long): Option[GroupedFileUpload] = Database.inTransaction {
      GroupedFileUploadFinder.byId(fileUploadId).headOption
    }

    override def fileContentStream(oid: Long): InputStream = new LargeObjectInputStream(oid)

    override def storeFile(file: GroupedProcessedFile): Unit = Database.inTransaction {
      GroupedProcessedFileStore.insertOrUpdate(file)
    }

    override def storeText(fileId: Long, text: String): Unit = Database.inTransaction {
      val fileText = FileText(fileId, text)
      BaseStore(Schema.fileTexts).insertOrUpdate(fileText)
    }
  }
  class PdfProcessorImpl extends PdfProcessor with PdfBoxPdfProcessor {
    override def textify(s: String) : String = Textify(s)
  }

  override val dataStore = new DataStoreImpl
  override val pdfProcessor = new PdfProcessorImpl
}

  

  
