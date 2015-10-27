package com.overviewdocs.jobhandler.filegroup.task

import akka.actor.ActorRef
import java.io.InputStream
import scala.concurrent.ExecutionContext
import scala.util.control.Exception.ultimately

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.GroupedFileUpload
import com.overviewdocs.postgres.LargeObjectInputStream
import com.overviewdocs.util.BulkDocumentWriter

class UploadedFileProcessCreator(
  val documentTypeDetector: DocumentTypeDetector,
  val bulkDocumentWriter: BulkDocumentWriter,
  val timeoutGenerator: TimeoutGenerator
) extends HasBlockingDatabase {
  def create(
    uploadedFile: GroupedFileUpload,
    options: UploadProcessOptions,
    documentSetId: Long,
    documentIdSupplier: ActorRef
  )(implicit ec: ExecutionContext): UploadedFileProcess = {
    withLargeObjectInputStream(uploadedFile.contentsOid) { stream =>
      val documentType = documentTypeDetector.detect(uploadedFile.name, stream)

      val parameters = FilePipelineParameters(
        documentSetId,
        uploadedFile,
        documentType,
        options,
        documentIdSupplier,
        bulkDocumentWriter,
        timeoutGenerator
      )

      new UploadedFileProcess(parameters)(ec)
    }
  }

  private def withLargeObjectInputStream[T](oid: Long)(f: InputStream => T): T = {
    val stream = largeObjectInputStream(oid)

    ultimately(stream.close) {
      f(stream)
    }
  }

  protected def largeObjectInputStream(oid: Long) = new LargeObjectInputStream(oid, blockingDatabase)
}

object UploadedFileProcessCreator extends HasBlockingDatabase {
  def apply(
    bulkDocumentWriter: BulkDocumentWriter,
    timeoutGenerator: TimeoutGenerator
  )(implicit ec: ExecutionContext): UploadedFileProcessCreator = new UploadedFileProcessCreator(
    DocumentTypeDetector,
    bulkDocumentWriter,
    timeoutGenerator
  )
}
