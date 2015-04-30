package org.overviewproject.jobhandler.filegroup

import scala.slick.jdbc.JdbcBackend.Session
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession

class DocumentIdSupplierSpec extends SlickSpecification {

  "DocumentIdSupplier" should {

    "return document ids when no document exists in document set" in new DocumentSetScope {
      val ids = documentIdSupplier.nextIds(5)

      ids must containTheSameElementsAs(expectedIds.take(5))
    }

    "return document ids when documents exist in document set" in new ExistingDocumentsScope {
      val ids = documentIdSupplier.nextIds(5)

      ids must containTheSameElementsAs(expectedIds.slice(1, 6))
    }

    "return valid ids for subsequent requests" in new DocumentSetScope {
      val firstIds = documentIdSupplier.nextIds(5)
      val secondIds = documentIdSupplier.nextIds(5)

      secondIds must containTheSameElementsAs(expectedIds.drop(5))
    }

    "return document ids when document set does not exist" in new DbScope {
      val documentIdSupplier = new TestDocumentIdSupplier(0)
      
      documentIdSupplier.nextIds(5) must haveSize(5)
    }

  }

  trait DocumentSetScope extends DbScope {
    val documentSet = factory.documentSet()
    val expectedIds = Seq.tabulate(10)(n => (documentSet.id << 32 | (n + 1)))

    val documentIdSupplier = new TestDocumentIdSupplier(documentSet.id)
  }

  trait ExistingDocumentsScope extends DocumentSetScope {
    val document = factory.document(id = (documentSet.id << 32) + 1, documentSetId = documentSet.id)
  }

  class TestDocumentIdSupplier(override protected val documentSetId: Long)(implicit val session: Session)
    extends DocumentIdSupplier with SlickClientInSession

}