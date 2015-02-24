package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

import org.overviewproject.database.{Database,SlickSessionProvider}
import org.overviewproject.database.orm.finders.FileFinder
import org.overviewproject.database.orm.finders.PageFinder
import org.overviewproject.models.Document
import org.overviewproject.models.tables.{DocumentSets,Documents,DocumentProcessingErrors}
import org.overviewproject.searchindex.TransportIndexClient
import org.overviewproject.tree.orm.File
import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
import org.overviewproject.tree.orm.finders.FinderById
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.util.SortedDocumentIdsRefresher

/**
 * Implementation of [[CreateDocumentsProcess]] with actual database queries
 */
trait CreateDocumentsWithStorage extends CreateDocumentsProcess {
  private val PageSize = 50

  override protected val searchIndex = TransportIndexClient.singleton
  
  override protected def getDocumentIdGenerator(documentSetId: Long): DocumentIdGenerator = DocumentIdGenerator(documentSetId)
  override protected val createDocumentsProcessStorage: CreateDocumentsProcessStorage = new DatabaseStorage

  protected class DatabaseStorage extends CreateDocumentsProcessStorage with SlickSessionProvider {
    private def await[A](f: Future[A]): A = {
      scala.concurrent.Await.result(f, scala.concurrent.duration.Duration.Inf)
    }

    override def findFilesQueryPage(documentSetId: Long, queryPage: Int): Iterable[File] = Database.inTransaction {
      FileFinder.byDocumentSetPaged(documentSetId, queryPage, PageSize).map(f => f.copy())
    }

    override def findFilePageText(fileId: Long): Iterable[PageText] = Database.inTransaction {
      PageFinder.byFileId(fileId).withoutData.map(p => PageText.tupled(p))
    }

    override def writeDocuments(documents: Iterable[Document]): Unit = await(db { session =>
      import org.overviewproject.database.Slick.simple._
      Documents.++=(documents)(session)
    })

    override def saveDocumentCount(documentSetId: Long): Unit = await(db { session =>
      import org.overviewproject.database.Slick.simple._
      val numberOfDocuments: Int = Documents
        .filter(_.documentSetId === documentSetId)
        .length.run(session)

      val numberOfDocumentProcessingErrors: Int = DocumentProcessingErrors
        .filter(_.documentSetId === documentSetId)
        .length.run(session)

      DocumentSets
        .filter(_.id === documentSetId)
        .map(ds => (ds.documentCount, ds.documentProcessingErrorCount))
        .update((numberOfDocuments, numberOfDocumentProcessingErrors))(session)
    })

    override def refreshSortedDocumentIds(documentSetId: Long): Unit = await {
      SortedDocumentIdsRefresher.refreshDocumentSet(documentSetId)
    }

    override def deleteTempFiles(documentSetId: Long): Unit = Database.inTransaction {
      import org.overviewproject.database.orm.Schema.tempDocumentSetFiles

      val tempDocumentSetFileStore = BaseStore(tempDocumentSetFiles)
      val tempDocumentSetFileFinder = DocumentSetComponentFinder(tempDocumentSetFiles)
      
      tempDocumentSetFileStore.delete(tempDocumentSetFileFinder.byDocumentSet(documentSetId).toQuery)
    }
  }
}
