package com.overviewdocs.jobhandler.filegroup.task

import akka.actor.ActorRef
import java.io.InputStream
import scala.concurrent.ExecutionContext
import scala.util.control.Exception.ultimately

import com.overviewdocs.models.GroupedFileUpload
import com.overviewdocs.util.BulkDocumentWriter

class UploadedFileProcessCreator(val bulkDocumentWriter: BulkDocumentWriter) {
  def create(
    upload: GroupedFileUpload,
    options: UploadProcessOptions,
    documentSetId: Long,
    documentIdSupplier: ActorRef
  )(implicit ec: ExecutionContext): UploadedFileProcess = {
    val parameters = FilePipelineParameters(
      documentSetId,
      upload,
      options,
      documentIdSupplier,
      bulkDocumentWriter
    )

    new UploadedFileProcess(parameters)(ec)
  }
}

object UploadedFileProcessCreator {
  def apply(bulkDocumentWriter: BulkDocumentWriter)(implicit ec: ExecutionContext): UploadedFileProcessCreator = {
    new UploadedFileProcessCreator(bulkDocumentWriter)
  }
}
