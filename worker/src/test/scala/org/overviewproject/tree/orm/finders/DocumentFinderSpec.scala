package org.overviewproject.tree.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.{ Document, DocumentSet }
import org.overviewproject.persistence.orm.Schema._
import org.overviewproject.persistence.orm.finders.DocumentFinder

class DocumentFinderSpec extends DbSpecification {

  step(setupDb)
  
  "DocumentFinder" should {
    
    trait DocumentContext extends DbTestContext {
      val documentIds = Seq(7l, 3l, 6l, 2l, 5l)
      var documentSet: DocumentSet = _
      
      override def setupWithDb = {
        documentSet = documentSets.insertOrUpdate(DocumentSet(title = "DocumentFinderSpec"))
        val docs = documentIds.map(n => Document(documentSet.id, id = n))
        documents.insert(docs)
      }
    }
    
   inExample("return documents ordered by id") in new DocumentContext {
      val docs = DocumentFinder.byDocumentSet(documentSet.id).orderedById
      
      docs.map(_.id) must be equalTo(documentIds.sorted)
    }
  }
  step(shutdownDb)
}