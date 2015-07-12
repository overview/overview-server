package org.overviewproject.jobhandler.filegroup.task

import java.io.InputStream

import scala.concurrent.ExecutionContext
import scala.util.control.Exception.ultimately

import org.overviewproject.database.HasBlockingDatabase
import org.overviewproject.jobhandler.filegroup.task.DocumentTypeDetector._
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromConvertedFile
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfFile
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentFromPdfPage
import org.overviewproject.jobhandler.filegroup.task.process.CreateDocumentsFromConvertedFilePages
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcess
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.postgres.LargeObjectInputStream
import org.overviewproject.util.BulkDocumentWriter

import akka.actor.ActorRef

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

object UploadedFileProcessCreator extends HasBlockingDatabase {

  case class UnsupportedDocumentTypeException(t: UnsupportedDocument) // FIXME: should not be an exception
    extends Exception(s"Unsupported Document type ${t.filename}: ${t.mimeType}")

  def apply(bulkDocumentWriter: BulkDocumentWriter, timeoutGenerator: TimeoutGenerator)(implicit executor: ExecutionContext): UploadedFileProcessCreator =
    new UploadedFileProcessCreatorImpl(bulkDocumentWriter, timeoutGenerator)

  private class UploadedFileProcessCreatorImpl(bulkDocumentWriter: BulkDocumentWriter,
      timeoutGenerator: TimeoutGenerator)(implicit executor: ExecutionContext) extends UploadedFileProcessCreator {
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
            CreateDocumentsFromConvertedFilePages(documentSetId, name, timeoutGenerator, documentIdSupplier, bulkDocumentWriter)
          case OfficeDocument =>
            CreateDocumentFromConvertedFile(documentSetId, name, timeoutGenerator, documentIdSupplier, bulkDocumentWriter)
          case t: UnsupportedDocument => throw new UnsupportedDocumentTypeException(t)
        }
    }

  }
}
