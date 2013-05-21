package org.overviewproject.database.orm.finders

import org.specs2.mutable.Specification
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification

class DocumentFinderSpec extends DbSpecification {
  
  step(setupDb)
  
  "DocumentFinder" should {
    
    "find all documents with specified DocumentCloudIds" in new DbTestContext {
      val documentSetId = insertDocumentSet("DocumentFinderSpec")
      val allDocumentIds = insertDocuments(documentSetId, 10)
      val documentCloudIds = Seq.tabulate(5)(n => s"documentCloudId-${n + 1}")
      val foundDocuments = DocumentFinder.byDocumentSetAndDocumentCloudIds(documentSetId, documentCloudIds)
      
      foundDocuments.map(_.id) must haveTheSameElementsAs(allDocumentIds.take(5))
    }
  }
  
  step(shutdownDb)

}