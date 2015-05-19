package org.overviewproject.jobhandler.filegroup.task

import akka.actor.Props
import org.overviewproject.test.ParameterStore
import akka.actor.ActorRef
import scala.concurrent.Future
import scala.concurrent.Promise
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.searchindex.ElasticSearchIndexClient
import org.overviewproject.jobhandler.filegroup.task.step.TaskStep
import org.overviewproject.jobhandler.filegroup.task.step.FinalStep

class TestFileGroupTaskWorker(jobQueuePath: String,
                              override protected val searchIndex: ElasticSearchIndexClient,
                              outputFileId: Long,
                              processStep: TaskStep = FinalStep) extends FileGroupTaskWorker {

  val updateDocumentSetInfoFn = ParameterStore[Long]
  val processUploadedFileFn = ParameterStore[(Long, Long, UploadProcessOptions, ActorRef)]

  override protected val jobQueueSelection = context.actorSelection(jobQueuePath)

  override protected def startDeleteFileUploadJob(documentSetId: Long, fileGroupId: Long): TaskStep =
    FinalStep

  override protected def processUploadedFile(documentSetId: Long, uploadedFileId: Long,
                                             options: UploadProcessOptions, documentIdSupplier: ActorRef): TaskStep = {
    processUploadedFileFn.store((documentSetId, uploadedFileId, options, documentIdSupplier))

    processStep
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
