package com.overviewdocs.jobhandler.filegroup

import akka.actor.ActorRef
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.models.GroupedFileUpload

/** Turns GroupedFileUploads into Documents (and DocumentProcessingErrors).
  */
class AddDocumentsImpl(documentIdSupplier: ActorRef) {
  /** Processes one GroupedFileUpload.
    *
    * By the time this Future succeeds, one of several side effects may have
    * occurred:
    *
    * * One File may have been written.
    * * One or more Pages may have been written.
    * * One or more Documents may have been written.
    * * One or more DocumentProcessingErrors may have been written.
    *
    * And one side-effect will certainly have occurred:
    *
    * * The GroupedFileUpload will be deleted, if it still exists.
    *
    * This method is resilient to every runtime error we expect. In particular:
    *
    * * If the DocumentSet gets deleted in a race, it will succeed.
    * * If the GroupedFileUpload gets deleted in a race, it will succeed.
    * * If the File gets deleted in a race, it will succeed.
    * * If the file cannot be parsed (e.g., it's an invalid PDF), it will write
    *   a DocumentProcessingError and succeed.
    *
    * If there's an error we *don't* expect (e.g., out of disk space), it will
    * return that error in the Future.
    */
  def processUpload(job: AddDocumentsJob, upload: GroupedFileUpload)(implicit ec: ExecutionContext): Future[Unit] = {
    // This is all icky wrapper stuff. TODO tidy it all.
    val parameters = task.FilePipelineParameters(
      job.documentSetId,
      upload,
      task.UploadProcessOptions(job.lang, job.splitDocuments),
      documentIdSupplier
    )
    new task.UploadedFileProcess(parameters).start
  }

  /** Deletes the Job from the database and freshens its DocumentSet info.
    *
    * In detail:
    *
    * 1. Ensures the DocumentSet has an alias in the search index.
    * 2. Updates the DocumentSet's document-ID array and counts.
    * 3. Deletes unprocessed GroupedFileUploads. (When the job is cancelled,
    *    these remain behind.)
    * 4. Deletes the DocumentSetCreationJob.
    * 5. Deletes the FileGroup.
    * 6. Creates a clustering job.
    */
  def finishJob(job: AddDocumentsJob)(implicit ec: ExecutionContext): Future[Unit] = {
    import com.overviewdocs.database.FileGroupDeleter
    import com.overviewdocs.database.DocumentSetCreationJobDeleter
    import com.overviewdocs.searchindex.TransportIndexClient
    for {
      _ <- TransportIndexClient.singleton.addDocumentSet(job.documentSetId) // FIXME move this to creation
      _ <- task.DocumentSetInfoUpdater.update(job.documentSetId)
      _ <- DocumentSetCreationJobDeleter.delete(job.documentSetCreationJobId)
      _ <- FileGroupDeleter.delete(job.fileGroupId)
      // _ <- [create clustering job...]
    } yield {
      ()
    }
  }
}
