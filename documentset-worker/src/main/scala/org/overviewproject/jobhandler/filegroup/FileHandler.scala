package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor
import org.overviewproject.tree.orm.FileUpload
import java.io.InputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.util.PDFTextStripper
import org.overviewproject.database.orm.finders.FileFinder
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.util.Logger
import org.overviewproject.database.DB
import org.overviewproject.database.Database
import org.overviewproject.database.orm.FileText
import org.overviewproject.database.orm.stores.FileTextStore
import org.overviewproject.database.orm.finders.FileUploadFinder
import org.overviewproject.tree.orm.File
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.database.orm.stores.FileStore
import org.overviewproject.util.ContentDisposition
import org.overviewproject.jobhandler.JobDone


object FileHandlerProtocol {
  case class ExtractText(fileGroupId: Long, uploadedFileId: Long)
}

trait FileHandlerComponents {

  val dataStore: DataStore
  val pdfProcessor: PdfProcessor

  trait DataStore {
    def findFileUpload(fileUploadId: Long): Option[FileUpload]
    def fileContentStream(oid: Long): InputStream
    def storeFile(fileGroupId: Long, file: File): Unit
    def storeText(fileId: Long, text: String): Unit
  }

  trait PdfProcessor {
    def extractText(fileStream: InputStream): String
  }
}

class FileHandler extends Actor {
  self: FileHandlerComponents =>

  import FileHandlerProtocol._

  def receive = {
    case ExtractText(fileGroupId, fileUploadId) => {
      val fileUpload = dataStore.findFileUpload(fileUploadId).get
      val fileStream = dataStore.fileContentStream(fileUpload.contentsOid)
      val text = pdfProcessor.extractText(fileStream)
      val file = File(
          fileUpload.guid,
          ContentDisposition.filename(fileUpload.contentDisposition).getOrElse("unknown name"),
          fileUpload.contentType,
          fileUpload.contentsOid,
          fileUpload.size,
          Complete,
          text,
          fileUpload.lastActivity)
      dataStore.storeFile(fileGroupId, file)
      
      sender ! JobDone(fileGroupId)
    }
  }
}

trait PdfBoxPdfProcessor {

  val pdfTextStripper = new PDFTextStripper

  def extractText(fileStream: InputStream): String = {
    val document = PDDocument.load(fileStream)

    pdfTextStripper.getText(document)
  }

}

class FileHandlerImpl extends FileHandler with FileHandlerComponents {
  class DataStoreImpl extends DataStore {
    override def findFileUpload(fileUploadId: Long): Option[FileUpload] = Database.inTransaction {
      FileUploadFinder.byId(fileUploadId).headOption
    } 
    
    override def fileContentStream(oid: Long): InputStream = new LargeObjectInputStream(oid)
    
    override def storeFile(fileGroupId: Long, file: File): Unit = Database.inTransaction {
      FileStore.insertWithFileGroup(fileGroupId, file)
    }
    
    override def storeText(fileId: Long, text: String): Unit = Database.inTransaction {
      val fileText = FileText(fileId, text)
      FileTextStore.insertOrUpdate(fileText)
    }
  }
  class PdfProcessorImpl extends PdfProcessor with PdfBoxPdfProcessor
  
  override val dataStore = new DataStoreImpl
  override val pdfProcessor = new PdfProcessorImpl
}

  

  
