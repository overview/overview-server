package org.overviewproject.jobhandler.filegroup.task

import akka.actor.ActorRef
import java.io.InputStream
import scala.util.control.Exception.ultimately

import org.overviewproject.database.BlockingDatabaseProvider
import org.overviewproject.jobhandler.filegroup.task.DocumentTypeDetector._
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromConvertedFile
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfFile
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfPage
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentsFromConvertedFilePages
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcess
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.util.BulkDocumentWriter

trait UploadedFileProcessCreator {

  def create(uploadedFile: GroupedFileUpload, options: UploadProcessOptions, documentSetId: Long,
             documentIdSupplier: ActorRef, bulkDocumentWriter: BulkDocumentWriter): UploadedFileProcess =
    withLargeObjectInputStream(uploadedFile.contentsOid) { stream =>

      val name = uploadedFile.name

      val documentType = documentTypeDetector.detect(name, stream)

      processMap.getProcess(documentType, options, documentSetId, name, documentIdSupplier)
    }

  private def withLargeObjectInputStream[T](oid: Long)(f: InputStream => T): T = {
    val stream = largeObjectInputStream(oid)

    ultimately(stream.close) {
      f(stream)
    }
  }

  protected val documentTypeDetector: DocumentTypeDetector
  protected def largeObjectInputStream(oid: Long): InputStream

  protected val processMap: ProcessMap

  protected trait ProcessMap {
    def getProcess(documentType: DocumentType, options: UploadProcessOptions,
                   documentSetId: Long, name: String,
                   documentIdSupplier: ActorRef): UploadedFileProcess
  }

}

object UploadedFileProcessCreator extends BlockingDatabaseProvider {

  def apply(bulkDocumentWriter: BulkDocumentWriter): UploadedFileProcessCreator = 
    new UploadedFileProcessCreatorImpl(bulkDocumentWriter)

  private class UploadedFileProcessCreatorImpl(bulkDocumentWriter: BulkDocumentWriter) extends UploadedFileProcessCreator {
    override protected val documentTypeDetector = DocumentTypeDetector
    override protected def largeObjectInputStream(oid: Long) = new LargeObjectInputStream(oid, blockingDatabase)

    override protected val processMap: ProcessMap = new UploadedFileProcessMap

    private class UploadedFileProcessMap extends ProcessMap {

      override def getProcess(documentType: DocumentType, options: UploadProcessOptions,
                              documentSetId: Long, name: String,
                              documentIdSupplier: ActorRef): UploadedFileProcess =
        documentType match {
          case PdfDocument if options.splitDocument =>
            CreateDocumentFromPdfPage(documentSetId, name, documentIdSupplier, bulkDocumentWriter)
          case PdfDocument =>
            CreateDocumentFromPdfFile(documentSetId, name, documentIdSupplier, bulkDocumentWriter)
          case OfficeDocument if options.splitDocument =>
            CreateDocumentsFromConvertedFilePages(documentSetId, name, documentIdSupplier, bulkDocumentWriter)
          case OfficeDocument =>
            CreateDocumentFromConvertedFile(documentSetId, name, documentIdSupplier, bulkDocumentWriter)
        }
    }

  }
}
