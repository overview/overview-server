package com.overviewdocs.jobhandler.filegroup.task

import akka.actor.Props
import com.overviewdocs.test.ParameterStore
import akka.actor.ActorRef
import scala.concurrent.Future
import scala.concurrent.Promise
import com.overviewdocs.models.GroupedFileUpload
import com.overviewdocs.searchindex.ElasticSearchIndexClient

class TestFileGroupTaskWorker(
  jobQueuePath: String,
  override protected val searchIndex: ElasticSearchIndexClient,
  outputFileId: Long
) extends FileGroupTaskWorker {

  val updateDocumentSetInfoFn = ParameterStore[Long]
  val processUploadedFileFn = ParameterStore[(Long, Long, UploadProcessOptions, ActorRef)]

  override protected val jobQueueSelection = context.actorSelection(jobQueuePath)

  override protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long) = Future.successful(())

  override protected def processUploadedFile(documentSetId: Long, uploadedFileId: Long,
                                             options: UploadProcessOptions, documentIdSupplier: ActorRef): Future[Unit] = {
    processUploadedFileFn.store((documentSetId, uploadedFileId, options, documentIdSupplier))

    Future.successful(())
  }

  override protected def updateDocumentSetInfo(documentSetId: Long) =
    Future.successful(updateDocumentSetInfoFn.store(documentSetId))

}

object TestFileGroupTaskWorker {
  def apply(
    jobQueuePath: String,
    searchIndex: ElasticSearchIndexClient,
    outputFileId: Long): Props =
    Props(new TestFileGroupTaskWorker(
      jobQueuePath,
      searchIndex,
      outputFileId))
}
