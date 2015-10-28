package com.overviewdocs.jobhandler.filegroup.task

import java.util.Date
import org.specs2.mock.Mockito
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.Document
import com.overviewdocs.models.tables.DocumentSets
import com.overviewdocs.test.DbSpecification
import com.overviewdocs.util.{BulkDocumentWriter,SortedDocumentIdsRefresher}

class DocumentSetInfoUpdaterSpec extends DbSpecification with Mockito {
  "DocumentSetInfoUpdater" should {
    "update the documentSet counts" in new DbScope {
      val documentSet = factory.documentSet()
      val documents = Seq.tabulate(3) { i => factory.document(documentSetId = documentSet.id, text=i.toString) }
      val documentProcessingErrors = Seq.fill(2)(factory.documentProcessingError(documentSetId = documentSet.id))

      await(DocumentSetInfoUpdater.update(documentSet.id))

      import database.api._

      val counts = blockingDatabase.option(
        DocumentSets
          .filter(_.id === documentSet.id)
          .map(ds => (ds.documentCount, ds.documentProcessingErrorCount))
      )
      counts must beSome((3, 2))
    }

    "sort document IDs" in new DbScope {
      val documentSet = factory.documentSet()
      val mockRefresher = smartMock[SortedDocumentIdsRefresher]
      mockRefresher.refreshDocumentSet(documentSet.id) returns Future.successful(())
      val updater = new DocumentSetInfoUpdater {
        override protected val sortedDocumentIdsRefresher = mockRefresher
      }

      await(updater.update(documentSet.id))

      there was one(mockRefresher).refreshDocumentSet(documentSet.id)
    }
  }
}
