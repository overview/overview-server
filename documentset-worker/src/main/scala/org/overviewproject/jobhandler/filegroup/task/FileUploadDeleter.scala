package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.database.Database
import org.overviewproject.database.orm.stores.GroupedFileUploadStore
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.Schema._
import org.overviewproject.database.orm.finders.FileGroupFinder
import org.overviewproject.database.orm.stores.PageStore
import org.overviewproject.database.orm.stores.FileStore
import org.overviewproject.tree.orm.finders.FinderById
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.database.orm.finders.DocumentFinder

trait FileUploadDeleter {

  protected trait Storage {
    def deleteGroupedFileUploads(fileGroupId: Long): Unit
    def deleteFileGroup(fileGroupId: Long): Unit
    def deletePages(documentSetId: Long): Unit
    def deleteFiles(documentSetId: Long): Unit
    def deleteTempDocumentSetFiles(documentSetId: Long): Unit
    def deleteDocumentProcessingErrors(documentSetId: Long): Unit
    def deleteDocuments(documentSetId: Long): Unit
    def deleteDocumentSet(documentSetId: Long): Unit
    def deleteDocumentSetCreationJob(documentSetId: Long): Unit
  }

  protected val storage: Storage

  def deleteFileUpload(documentSetId: Long, fileGroupId: Long): Unit = {
    storage.deletePages(documentSetId)
    storage.deleteFiles(documentSetId)
    storage.deleteTempDocumentSetFiles(documentSetId)
    storage.deleteDocumentProcessingErrors(documentSetId)
    storage.deleteDocumentSetCreationJob(documentSetId)
    storage.deleteDocuments(documentSetId)
    storage.deleteDocumentSet(documentSetId)
    storage.deleteGroupedFileUploads(fileGroupId)
    storage.deleteFileGroup(fileGroupId)

  }
}

object FileUploadDeleter {
  def apply(): FileUploadDeleter = new FileUploadDeleter {

    protected class DatabaseStorage extends Storage {

      override def deleteGroupedFileUploads(fileGroupId: Long): Unit = Database.inTransaction {
        GroupedFileUploadStore.deleteLargeObjectsInFileGroup(fileGroupId)
        GroupedFileUploadStore.deleteByFileGroup(fileGroupId)
      }

      override def deleteFileGroup(fileGroupId: Long): Unit = Database.inTransaction {
        val fileGroupStore = new BaseStore(fileGroups)

        fileGroupStore.delete(FileGroupFinder.byId(fileGroupId).toQuery)
      }

      override def deletePages(documentSetId: Long): Unit = Database.inTransaction {
        PageStore.removeReferenceByTempDocumentSet(documentSetId)
      }

      override def deleteFiles(documentSetId: Long): Unit = Database.inTransaction {
    	FileStore.removeReferenceByTempDocumentSet(documentSetId)
      }

      override def deleteTempDocumentSetFiles(documentSetId: Long): Unit = Database.inTransaction {
        val tempDocumentSetFileFinder = DocumentSetComponentFinder(tempDocumentSetFiles)
        val tempDocumentSetFileStore = BaseStore(tempDocumentSetFiles)

        tempDocumentSetFileStore.delete(tempDocumentSetFileFinder.byDocumentSet(documentSetId).toQuery)
      }

      override def deleteDocumentProcessingErrors(documentSetId: Long): Unit = Database.inTransaction {
        val documentProcessingErrorFinder = DocumentSetComponentFinder(documentProcessingErrors)
        val documentProcessingErrorStore = BaseStore(documentProcessingErrors)
        
        documentProcessingErrorStore.delete(documentProcessingErrorFinder.byDocumentSet(documentSetId).toQuery)
      }
      
      override def deleteDocuments(documentSetId: Long): Unit = Database.inTransaction {
        val documentStore = BaseStore(documents)
        
        documentStore.delete(DocumentFinder.byDocumentSet(documentSetId).toQuery)  
      }
      
      override def deleteDocumentSet(documentSetId: Long): Unit = Database.inTransaction {
        val documentSetFinder = new FinderById(documentSets)
        val documentSetUserFinder = DocumentSetComponentFinder(documentSetUsers)
        val documentSetStore = BaseStore(documentSets)
        val documentSetUserStore = BaseStore(documentSetUsers)
        
        documentSetUserStore.delete(documentSetUserFinder.byDocumentSet(documentSetId).toQuery)
        documentSetStore.delete(documentSetFinder.byId(documentSetId).toQuery)
      }

      override def deleteDocumentSetCreationJob(documentSetId: Long): Unit = Database.inTransaction {
        DocumentSetCreationJobStore.deleteByState(documentSetId, Cancelled)
      }
    }

    override protected val storage = new DatabaseStorage
  }
}