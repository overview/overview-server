package org.overviewproject.jobhandler.filegroup.task

import org.overviewproject.tree.orm.File
import org.overviewproject.tree.orm.Document
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.FileFinder
import org.overviewproject.database.orm.finders.PageFinder
import org.overviewproject.tree.orm.stores.BaseStore

trait CreateDocumentsWithStorage extends CreateDocumentsProcess {
  private val PageSize = 50
  
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
    
  }
}