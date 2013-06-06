package models.orm.stores

import org.squeryl.{ KeyedEntityDef, Query }

import org.overviewproject.tree.orm._
import models.orm.finders._
import models.orm.stores._
import models.orm.Schema 

object DocumentSetStore extends BaseStore(models.orm.Schema.documentSets) {
  override def delete(query: Query[DocumentSet]) : Int = {
    throw new AssertionError("Use DocumentSet.deleteOrCancelJob(), not delete()")
  }

  override def delete[K](k: K)(implicit ked: KeyedEntityDef[DocumentSet,K]) : Unit = {
    throw new AssertionError("Use DocumentSet.deleteOrCancelJob(), not delete()")
  }

  def deleteOrCancelJob(query: Query[DocumentSet]) : Unit = {
    FinderResult(query).foreach(deleteOrCancelJob(_))
  }

  private def deleteAssumingJobDoesNotExist(documentSet: Long) : Unit = {
    deleteClientGeneratedInformation(documentSet)
    deleteClusteringGeneratedInformation(documentSet)
    deleteDocumentSetAfterMostReferencesAreDeleted(documentSet)
  }

  /** Deletes a DocumentSet, or tells the worker to do so.
    *
    * This method has many side-effects: every database object related to the
    * DocumentSet will be deleted. Every clone job will be deleted (but clones
    * will remain valid, if their jobs are already complete).
    *
    * This method is slow.
    *
    * TODO: Make the worker and only the worker create and delete document
    * sets ... then make this method a one-liner.
    */
  def deleteOrCancelJob(documentSet: Long) : Unit = {
    deleteOrCancelPendingClones(documentSet)

    DocumentSetCreationJobFinder.byDocumentSet(documentSet).forUpdate.headOption match {
      case Some(job) => 
        if (job.state == DocumentSetCreationJobState.InProgress || job.state == DocumentSetCreationJobState.Cancelled) {
          DocumentSetCreationJobStore.cancel(job)
          deleteClientGeneratedInformation(documentSet)
        } else {
          DocumentSetCreationJobStore.deletePending(job)
          deleteAssumingJobDoesNotExist(documentSet)
        }
      case None =>
        deleteAssumingJobDoesNotExist(documentSet)
    }
  }

  /** Calls deleteOrCancel() on all DocumentSets that are clones of the given
    * DocumentSet which have NotStarted or InProgress jobs.
    */
  private def deleteOrCancelPendingClones(documentSet: Long) : Unit = {
    val jobs = FinderResult(
      DocumentSetCreationJobFinder
        .bySourceDocumentSet(documentSet)
        .byState(DocumentSetCreationJobState.NotStarted, DocumentSetCreationJobState.InProgress)
        .forUpdate
    )
    jobs.foreach((dscj: DocumentSetCreationJob) => deleteOrCancelJob(dscj.documentSetId))
  }

  private def deleteClientGeneratedInformation(documentSet: Long) : Unit = {
    LogEntryStore.delete(LogEntryFinder.byDocumentSet(documentSet).toQuery)
    DocumentTagStore.delete(DocumentTagFinder.byDocumentSet(documentSet).toQuery)
    TagStore.delete(TagFinder.byDocumentSet(documentSet).toQuery)
    DocumentSetUserStore.delete(DocumentSetUserFinder.byDocumentSet(documentSet).toQuery)
  }

  private def deleteClusteringGeneratedInformation(documentSet: Long) : Unit = {
    DocumentSetCreationJobFinder.byDocumentSet(documentSet).forUpdate.headOption.map(DocumentSetCreationJobStore.deletePending)
    NodeDocumentStore.delete(NodeDocumentFinder.byDocumentSet(documentSet).toQuery)
    NodeStore.delete(NodeFinder.byDocumentSet(documentSet).toQuery)
    DocumentStore.delete(DocumentFinder.byDocumentSet(documentSet).toQuery)
    DocumentProcessingErrorStore.delete(DocumentProcessingErrorFinder.byDocumentSet(documentSet).toQuery)
  }

  private def deleteDocumentSetAfterMostReferencesAreDeleted(documentSet: Long) : Unit = {
    import org.overviewproject.postgres.SquerylEntrypoint._

    // There's a foreign key from UploadedFile to DocumentSet.
    // Delete the DocumentSet, then any UploadedFile that it refers to.
    val uploadedFiles : Iterable[UploadedFile] = UploadedFileFinder.byDocumentSet(documentSet).toIterable
    Schema.documentSets.delete(documentSet)
    uploadedFiles.map((uf: UploadedFile) => UploadedFileStore.delete(uf.id))
  }
}
