package org.overviewproject.util

import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.Documents

class DocumentSetCleanerSpec extends SlickSpecification {

  
  "DocumentSetCleaner" should {
    
    "delete documents" in new DocumentSetScope {
     await(cleaner.deleteDocuments(documentSet.id))
     
     Documents.filter(_.documentSetId === documentSet.id).list must beEmpty
    }
  }
  
  trait DocumentSetScope extends DbScope {
    val cleaner = new TestDocumentSetCleaner
    val documentSet = factory.documentSet()
    val document = factory.document(documentSetId = documentSet.id)
    
  }
  
  class TestDocumentSetCleaner(implicit val session: Session) extends DocumentSetCleaner with SlickClientInSession
}