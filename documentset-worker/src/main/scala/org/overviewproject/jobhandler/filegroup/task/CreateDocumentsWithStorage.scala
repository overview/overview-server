package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.tree.orm.File
import org.overviewproject.tree.orm.Document
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.FileFinder
import org.overviewproject.database.orm.finders.PageFinder
import org.overviewproject.database.orm.Schema.{ documentProcessingErrors, documentSets }
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.database.orm.finders.DocumentFinder
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.database.orm.finders.FinderById
import org.overviewproject.util.SearchIndex

trait CreateDocumentsWithStorage extends CreateDocumentsProcess {
  private val PageSize = 50

  override protected val searchIndex = SearchIndex
  override protected def getDocumentIdGenerator(documentSetId: Long): DocumentIdGenerator = DocumentIdGenerator(documentSetId)
  override protected val createDocumentsProcessStorage: CreateDocumentsProcessStorage = new DatabaseStorage

  protected class DatabaseStorage extends CreateDocumentsProcessStorage {
    import org.overviewproject.database.orm.Schema.documents
    private val documentStore = BaseStore(documents)

    def findFilesQueryPage(documentSetId: Long, queryPage: Int): Iterable[File] = Database.inTransaction {
      FileFinder.byDocumentSetPaged(documentSetId, queryPage, PageSize).map(f => f.copy())
    }

    def findFilePageData(fileId: Long): Iterable[(Long, Int, Option[String])] = Database.inTransaction {
      PageFinder.byFileId(fileId).withoutData.map(_.copy())
    }

    def writeDocuments(documents: Iterable[Document]): Unit = Database.inTransaction {
      documentStore.insertBatch(documents)
    }

    def saveDocumentCount(documentSetId: Long): Unit = Database.inTransaction {
      val documentProcessingErrorFinder = DocumentSetComponentFinder(documentProcessingErrors)
      val documentSetFinder = new FinderById(documentSets)
      val documentSetStore = BaseStore(documentSets)

      val numberOfDocuments = DocumentFinder.byDocumentSet(documentSetId).count.toInt
      val numberOfDocumentProcessingErrors = documentProcessingErrorFinder.byDocumentSet(documentSetId).count.toInt

      val documentSet = documentSetFinder.byId(documentSetId).headOption

      documentSet.map { ds =>
        val updatedDocumentSet = ds.copy(documentCount = numberOfDocuments,
          documentProcessingErrorCount = numberOfDocumentProcessingErrors)

        documentSetStore.insertOrUpdate(updatedDocumentSet)
      }
    }

    def deleteTempFiles(documentSetId: Long): Unit = Database.inTransaction {
      import org.overviewproject.database.orm.Schema.tempDocumentSetFiles

      val tempDocumentSetFileStore = BaseStore(tempDocumentSetFiles)
      val tempDocumentSetFileFinder = DocumentSetComponentFinder(tempDocumentSetFiles)
      
      tempDocumentSetFileStore.delete(tempDocumentSetFileFinder.byDocumentSet(documentSetId).toQuery)
    }
  }
}