package org.overviewproject.jobhandler

import akka.actor.Actor
import org.overviewproject.tree.orm.File
import java.io.InputStream

object FileHandlerProtocol {
  case class ExtractText(documentSetId: Long, fileId: Long)
}

trait FileHandlerComponents {

  val dataStore: DataStore
  val pdfProcessor: PdfProcessor 
  
  trait DataStore {
    def findFile(fileId: Long): Option[File]
    def fileContentStream(oid: Long): InputStream
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
    }
  }
}