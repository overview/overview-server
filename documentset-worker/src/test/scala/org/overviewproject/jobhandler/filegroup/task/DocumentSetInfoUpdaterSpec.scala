package org.overviewproject.jobhandler.filegroup.task

import java.util.Date
import slick.jdbc.JdbcBackend.{ Session => JSession }
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession
import org.overviewproject.models.Document
import org.overviewproject.models.tables.DocumentSets
import org.overviewproject.database.Slick.simple._
import org.specs2.mock.Mockito
import org.overviewproject.util.SortedDocumentIdsRefresher
import scala.concurrent.Future
import org.overviewproject.util.BulkDocumentWriter

class DocumentSetInfoUpdaterSpec extends SlickSpecification with Mockito {

  "DocumentSetInfoUpdater" should {

    "update document counts" in new DocumentSetScope {
      await(documentSetInfoUpdater.update(documentSet.id))

      val info = DocumentSets
        .filter(_.id === documentSet.id)
        .map(ds => (ds.documentCount, ds.documentProcessingErrorCount)).firstOption

      info must beSome((numberOfDocuments, numberOfDocumentProcessingErrors))
    }

  }

  trait DocumentSetScope extends DbScope with Mockito {
    val numberOfDocuments = 5
    val numberOfDocumentProcessingErrors = 3
    val bulkWriter = BulkDocumentWriter.forDatabaseAndSearchIndex

    val documentSet = factory.documentSet(documentCount = 0, documentProcessingErrorCount = 0)
    val documents = Seq.fill(numberOfDocuments)(factory.document(documentSetId = documentSet.id))

    val documentProcessingErrors =
      Seq.fill(numberOfDocumentProcessingErrors)(factory.documentProcessingError(documentSetId = documentSet.id))

    val documentSetInfoUpdater = new TestDocumentSetInfoUpdater

    def addDocuments: Future[Seq[Unit]] = Future.sequence {
      documents.map(bulkWriter.addAndFlushIfNeeded)
    }

    class TestDocumentSetInfoUpdater(implicit val session: JSession) extends DocumentSetInfoUpdater with SlickClientInSession {
      override protected val bulkDocumentWriter = smartMock[BulkDocumentWriter]
      bulkDocumentWriter.flush returns Future.successful(())

      private val documentIdsRefresher = smartMock[SortedDocumentIdsRefresher]
      documentIdsRefresher.refreshDocumentSet(documentSet.id) returns Future.successful(())

      override def refreshDocumentSet(documentSetId: Long) = documentIdsRefresher.refreshDocumentSet(documentSetId)
    }
  }
}