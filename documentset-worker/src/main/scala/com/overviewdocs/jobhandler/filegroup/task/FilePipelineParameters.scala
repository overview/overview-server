package com.overviewdocs.jobhandler.filegroup.task

import akka.actor.ActorRef
import java.util.{Locale,UUID}

import com.overviewdocs.models.GroupedFileUpload
import com.overviewdocs.util.BulkDocumentWriter

case class FilePipelineParameters(
  documentSetId: Long,
  upload: GroupedFileUpload,
  options: UploadProcessOptions,
  documentIdSupplier: ActorRef,
  bulkDocumentWriter: BulkDocumentWriter
) {
  def lang: String = options.lang
  def ocrLocales: Seq[Locale] = Seq(new Locale(lang))
  def splitDocuments: Boolean = options.splitDocuments
  def filename: String = upload.name
  def guid: UUID = upload.guid
  def fileGroupId: Long = upload.fileGroupId
  def inputOid: Long = upload.contentsOid
  def inputSize: Long = upload.size
}
