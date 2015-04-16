package org.overviewproject.jobhandler.filegroup.task

import scala.util.control.Exception.ultimately
import java.io.InputStream
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcessCreator
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfFile
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfPage
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromConvertedFile
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentsFromConvertedFilePages
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.database.SlickSessionProvider

trait UploadProcessSelector {
  import DocumentTypeDetector._

  def select(uploadedFile: GroupedFileUpload, options: UploadProcessOptions): UploadedFileProcessCreator =
    withLargeObjectInputStream(uploadedFile.contentsOid) { stream =>
      documentTypeDetector.detect(uploadedFile.name, stream) match {
        case PdfDocument if options.splitDocument => CreateDocumentFromPdfPage
        case PdfDocument => CreateDocumentFromPdfFile
        case OfficeDocument if options.splitDocument => CreateDocumentsFromConvertedFilePages
        case OfficeDocument => CreateDocumentFromConvertedFile
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

object UploadProcessSelector {
  
  def apply(): UploadProcessSelector = new UploadProcessSelectorImpl
  
  private class UploadProcessSelectorImpl extends UploadProcessSelector {
    override protected val documentTypeDetector = DocumentTypeDetector
    override protected def largeObjectInputStream(oid: Long) = 
      new LargeObjectInputStream(oid, new SlickSessionProvider {})
  } 
}