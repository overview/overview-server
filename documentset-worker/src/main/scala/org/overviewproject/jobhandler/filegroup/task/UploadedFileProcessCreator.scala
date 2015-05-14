package org.overviewproject.jobhandler.filegroup.task

import scala.util.control.Exception.ultimately
import java.io.InputStream
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfFile
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfPage
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromConvertedFile
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentsFromConvertedFilePages
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.database.SlickSessionProvider
import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcess

trait UploadedFileProcessCreator {
  import DocumentTypeDetector._

  def create(uploadedFile: GroupedFileUpload, options: UploadProcessOptions,
      documentSetId: Long, documentIdSupplier: ActorRef): UploadedFileProcess =
    withLargeObjectInputStream(uploadedFile.contentsOid) { stream =>
      documentTypeDetector.detect(uploadedFile.name, stream) match {
        case PdfDocument if options.splitDocument => CreateDocumentFromPdfPage(documentSetId, documentIdSupplier)
        case PdfDocument => CreateDocumentFromPdfFile(documentSetId, documentIdSupplier)
        case OfficeDocument if options.splitDocument => CreateDocumentsFromConvertedFilePages(documentSetId, documentIdSupplier)
        case OfficeDocument => CreateDocumentFromConvertedFile(documentSetId, documentIdSupplier)
      }
    }

  protected val documentTypeDetector: DocumentTypeDetector
  protected def largeObjectInputStream(oid: Long): InputStream

  private def withLargeObjectInputStream[T](oid: Long)(f: InputStream => T): T = {
    val stream = largeObjectInputStream(oid)

    ultimately(stream.close) {
      f(stream)
    }
  }

}

object UploadedFileProcessCreator {
  
  def apply(): UploadedFileProcessCreator = new UploadedFileProcessCreatorImpl
  
  private class UploadedFileProcessCreatorImpl extends UploadedFileProcessCreator {
    override protected val documentTypeDetector = DocumentTypeDetector
    override protected def largeObjectInputStream(oid: Long) = 
      new LargeObjectInputStream(oid, new SlickSessionProvider {})
  } 
}
