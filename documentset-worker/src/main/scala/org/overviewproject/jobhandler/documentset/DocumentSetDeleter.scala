package org.overviewproject.jobhandler.documentset

import org.overviewproject.tree.orm.UploadedFile
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.tree.orm.stores.BaseNodeStore
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

/**
 * Methods for deleting all the data associated with document sets in the database
 * Client code needs to manage the proper order of calls.
 */
trait DocumentSetDeleter {
  def deleteJobInformation(documentSetId: Long): Unit
  def deleteCancelledJobInformation(documentSetId: Long): Unit
  def deleteOneCancelledJobInformation(jobId: Long): Unit
  def deleteClientGeneratedInformation(documentSetId: Long): Unit
  def deleteClusteringGeneratedInformation(documentSetId: Long): Unit
  def deleteDocumentSet(documentSetId: Long): Unit

}

object DocumentSetDeleter {
  import org.overviewproject.postgres.SquerylEntrypoint._
  import org.overviewproject.database.Database
  import org.overviewproject.database.orm.finders._
  import org.overviewproject.database.orm.stores._
  import org.overviewproject.database.orm.Schema._
  import org.overviewproject.tree.orm.stores.BaseStore
  import org.overviewproject.tree.orm.DocumentSetComponent
  import org.squeryl.Table

  def apply() = new DocumentSetDeleter {

    def deleteJobInformation(documentSetId: Long): Unit = Database.inTransaction {
      implicit val id = documentSetId

      deleteNonRunningJobs
    }

    def deleteCancelledJobInformation(documentSetId: Long): Unit = Database.inTransaction {
      deleteCancelledJobsInDocumentSet(documentSetId)
    }

    def deleteOneCancelledJobInformation(jobId: Long): Unit = Database.inTransaction {
      DocumentSetCreationJobStore.delete(jobId)
    }

    def deleteClientGeneratedInformation(documentSetId: Long): Unit = Database.inTransaction {
      implicit val id = documentSetId

      delete(logEntries)
      delete(documentTags, DocumentTagFinder)
      delete(tags)
      delete(documentSearchResults, DocumentSearchResultFinder)
      delete(searchResults)
    }

    def deleteClusteringGeneratedInformation(documentSetId: Long): Unit = Database.inTransaction {
      implicit val id = documentSetId

      delete(nodeDocuments, NodeDocumentFinder)
      deleteNodes
      delete(trees)
      delete(documentProcessingErrors)
    }

    def deleteUserRelatedInformation(documentSetId: Long): Unit = Database.inTransaction {
      implicit val id = documentSetId

      delete(documentSetUsers)
    }

    def deleteDocumentSet(documentSetId: Long): Unit = Database.inTransaction {
      implicit val id = documentSetId

      val fileIds = findFileIds

      delete(documents)
      delete(documentSetUsers)

      delete(tempDocumentSetFiles)
      
      deletePages(fileIds)
      deleteFiles(fileIds)

      val uploadedFile = findUploadedFile

      deleteDocumentSetById(documentSetId)

      deleteUploadedFile(uploadedFile)

    }
  }

  private def deleteNodes(implicit documentSetId: Long): Unit = {
    val nodeStore = BaseNodeStore(nodes, trees)
    nodeStore.deleteByDocumentSet(documentSetId)
  }

  private def findFileIds(implicit documentSetId: Long): Seq[Long] =
    DocumentFinder.byDocumentSet(documentSetId).toFileIds.flatten.toSeq

  private def deleteNonRunningJobs(implicit documentSetId: Long): Unit =
    DocumentSetCreationJobStore.deleteNonRunningJobs(documentSetId)

  private def deleteCancelledJob(jobId: Long): Unit = {
    // We don't care what state the job is in. If we got this message, it was
    // Cancelled at some point.
    DocumentSetCreationJobStore.delete(jobId)
  }

  private def deleteCancelledJobsInDocumentSet(documentSetId: Long): Unit =
    DocumentSetCreationJobStore.deleteByState(documentSetId, Cancelled)

  private def deleteFiles(fileIds: Seq[Long]): Unit = FileStore.removeReference(fileIds)

  private def deletePages(fileIds: Seq[Long]): Unit = PageStore.removeReferenceByFile(fileIds)

  private def deleteDocumentContents(implicit documentSetId: Long): Unit = {
    FileStore.deleteLargeObjectsByDocumentSet(documentSetId)
  }

  private def findUploadedFile(implicit documentSetId: Long): Option[UploadedFile] =
    UploadedFileFinder.byDocumentSet(documentSetId).headOption

  private def deleteUploadedFile(uploadedFile: Option[UploadedFile]): Unit =
    uploadedFile.foreach(uf => BaseStore(uploadedFiles).delete(uf.id))

  private def deleteDocumentSetById(documentSetId: Long) = {
    val documentSetFinder = new FinderById(documentSets)
    BaseStore(documentSets).delete(documentSetFinder.byId(documentSetId).toQuery)
  }

  private def delete[A <: DocumentSetComponent](table: Table[A])(implicit documentSetId: Long) =
    BaseStore(table).delete(DocumentSetComponentFinder(table).byDocumentSet(documentSetId).toQuery)

  private def delete[A](table: Table[A], finder: FindableByDocumentSet[A])(implicit documentSetId: Long) =
    BaseStore(table).delete(finder.byDocumentSet(documentSetId).toQuery)
}
