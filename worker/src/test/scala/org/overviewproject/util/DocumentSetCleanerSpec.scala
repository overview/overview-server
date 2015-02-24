package org.overviewproject.util

import scala.slick.jdbc.JdbcBackend.Session

import org.overviewproject.test.{ DbSpecification, SlickClientInSession }
import org.overviewproject.models.tables.Documents

class DocumentSetCleanerSpec extends DbSpecification {
  "DocumentSetCleaner" should {
    "delete documents" in new DocumentSetScope {
      await(cleaner.deleteDocuments(documentSet.id))

      import org.overviewproject.database.Slick.simple._
      Documents.filter(_.documentSetId === documentSet.id).list(session) must beEmpty
    }
  }

  trait DocumentSetScope extends DbScope {
    val cleaner = new TestDocumentSetCleaner(session)
    val documentSet = factory.documentSet()
    val document = factory.document(documentSetId = documentSet.id)
  }

  class TestDocumentSetCleaner(val session: Session) extends DocumentSetCleaner with SlickClientInSession
}
