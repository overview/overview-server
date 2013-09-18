package models.orm.stores

import java.sql.Timestamp

import org.overviewproject.tree.orm.{ DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, UploadedFile }
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.tree.{ DocumentSetCreationJobType, Ownership }
import org.overviewproject.tree.orm.DocumentSetUser
import models.orm.finders.{ DocumentSetFinder, UploadedFileFinder }
import models.CloneImportJob

object CloneImportJobStore {
  /** Creates a clone DocumentSet, without a DocumentSetCreationJob.
    *
    * The caller must create a DocumentSetCreationJob in the same transaction.
    *
    * TODO: make it so that only the worker creates DocumentSets, then remove
    * this method.
    */
  private[stores] def insertCloneOf(original: DocumentSet) : DocumentSet = {
    val originalUploadedFile = original.uploadedFileId.flatMap(id =>
      UploadedFileFinder.byUploadedFile(id).headOption
    )
    val cloneFile = originalUploadedFile.map { (uf: UploadedFile) =>
      UploadedFileStore.insertOrUpdate(uf.copy(id=0L))
    }

    DocumentSetStore.insertOrUpdate(original.copy(
      id = 0L,
      isPublic = false,
      createdAt = new Timestamp(scala.compat.Platform.currentTime),
      uploadedFileId = cloneFile.map(_.id)
    ))
  }

  /** Adds a CloneImportJob in the database.
    *
    * FIXME this inserts rows in DocumentSet and DocumentSetUser. Both stores
    * should be left alone. (First we need to make the worker add those rows.)
    */
  def insert(job: CloneImportJob) : DocumentSetCreationJob = {
    val originalDocumentSet = DocumentSetFinder.byDocumentSet(job.sourceDocumentSetId).headOption
      .getOrElse(throw new Exception("Rather than handle this error, let's just move this task to the worker"))
    val documentSet = insertCloneOf(originalDocumentSet)

    DocumentSetUserStore.insertOrUpdate(DocumentSetUser(
      documentSetId=documentSet.id,
      userEmail=job.ownerEmail,
      role=Ownership.Owner
    ))

    DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
      documentSetId=documentSet.id,
      state=DocumentSetCreationJobState.NotStarted,
      jobType=DocumentSetCreationJobType.Clone,
      sourceDocumentSetId=Some(job.sourceDocumentSetId)
    ))
  }
}
