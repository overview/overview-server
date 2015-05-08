package org.overviewproject.jobhandler.filegroup.task

import scala.slick.jdbc.JdbcBackend.{ Session => JSession }
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession
import org.overviewproject.models.tables.DocumentSets
import org.overviewproject.database.Slick.simple._
import org.specs2.mock.Mockito
import org.overviewproject.util.SortedDocumentIdsRefresher
import scala.concurrent.Future

class DocumentSetInfoUpdaterSpec extends SlickSpecification with Mockito {

  "DocumentSetInfoUpdater" should {

    "update document counts" in new DocumentSetScope {
      await(documentSetInfoUpdater.update(documentSet.id))

      val info = DocumentSets
        .filter(_.id === documentSet.id)
        .map(ds => (ds.documentCount, ds.documentProcessingErrorCount)).firstOption

      info must beSome((numberOfDocuments, numberOfDocumentProcessingErrors))
    }

    "refresh sorted documentIds" in new DocumentSetScope {
      val r = documentSetInfoUpdater.update(documentSet.id)

      await(r)
      
      r.isCompleted must beTrue
      
    }
  }

  trait DocumentSetScope extends DbScope {
    val numberOfDocuments = 5
    val numberOfDocumentProcessingErrors = 3

    val documentSet = factory.documentSet(documentCount = 0, documentProcessingErrorCount = 0)
    val documents = Seq.fill(numberOfDocuments)(factory.document(documentSetId = documentSet.id))

    val documentProcessingErrors =
      Seq.fill(numberOfDocumentProcessingErrors)(factory.documentProcessingError(documentSetId = documentSet.id))

    val documentIdsRefresher = smartMock[SortedDocumentIdsRefresher]
    documentIdsRefresher.refreshDocumentSet(documentSet.id) returns Future.successful(())
    
    val documentSetInfoUpdater = new TestDocumentSetInfoUpdater

    class TestDocumentSetInfoUpdater(implicit val session: JSession) extends DocumentSetInfoUpdater with SlickClientInSession {
      override def refreshDocumentSet(documentSetId: Long) = documentIdsRefresher.refreshDocumentSet(documentSetId)
    }
  }
}