package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor
import org.overviewproject.tree.orm.File
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


object FileHandlerProtocol {
  case class ExtractText(documentSetId: Long, fileId: Long)
  case object JobDone
}

trait FileHandlerComponents {

  val dataStore: DataStore
  val pdfProcessor: PdfProcessor

  trait DataStore {
    def findFile(fileId: Long): Option[File]
    def fileContentStream(oid: Long): InputStream
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
    case ExtractText(documentSetId, fileId) => {
      val file = dataStore.findFile(fileId).get
      val fileStream = dataStore.fileContentStream(file.contentsOid)
      val text = pdfProcessor.extractText(fileStream)
      dataStore.storeText(fileId, text)
      
      sender ! JobDone
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
    override def findFile(fileId: Long): Option[File] = Database.inTransaction {
      FileFinder.byId(fileId).headOption
    } 
    
    override def fileContentStream(oid: Long): InputStream = new LargeObjectInputStream(oid)
    
    override def storeText(fileId: Long, text: String): Unit = {
      val fileText = FileText(fileId, text)
      FileTextStore.insertOrUpdate(fileText)
    }
  }
  class PdfProcessorImpl extends PdfProcessor with PdfBoxPdfProcessor
  
  override val dataStore = new DataStoreImpl
  override val pdfProcessor = new PdfProcessorImpl
}

  

  
