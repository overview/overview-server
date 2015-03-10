package models.orm.stores

import java.util.Date
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.DocumentSet
import models.orm.finders.DocumentSetFinder

class DocumentSetStoreSpec extends DbSpecification {
  trait DocumentSetContext extends DbTestContext {
    def insertDocumentSet = {
      DocumentSetStore.insertOrUpdate(DocumentSet(title="title", query=Some("query")))
    }
  }

  "DocumentSetStore" should {
    "set createdAt to the current date by default" in new Scope {
      DocumentSet().createdAt.getTime must beCloseTo((new Date().getTime), 1000)
    }

    "set isPublic to false by default" in new Scope {
      DocumentSet().isPublic must beFalse
    }

    "set deleted to false by default" in new Scope {
      DocumentSet().deleted must beFalse
    }

    "set document set deleted flag to true on delete" in new DocumentSetContext {
      val documentSet = insertDocumentSet
      
      DocumentSetStore.markDeleted(documentSet)
      
      val deletedDocumentSet = DocumentSetFinder.byDocumentSet(documentSet.id).headOption
      
      deletedDocumentSet must beSome.like { case ds => ds.deleted must beTrue }
    }
  }
}
