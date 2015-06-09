package org.overviewproject.jobhandler.filegroup

import slick.jdbc.JdbcBackend.Session
import org.overviewproject.test.DbSpecification

class DocumentIdGeneratorSpec extends DbSpecification {

  "DocumentIdGenerator" should {
    "return document ids when no document exists in document set" in new DocumentSetScope {
      val ids = documentIdGenerator.nextIds(5)

      ids must containTheSameElementsAs(expectedIds.take(5))
    }

    "return document ids when documents exist in document set" in new ExistingDocumentsScope {
      val ids = documentIdGenerator.nextIds(5)

      ids must containTheSameElementsAs(expectedIds.slice(1, 6))
    }

    "return valid ids for subsequent requests" in new DocumentSetScope {
      val firstIds = documentIdGenerator.nextIds(5)
      val secondIds = documentIdGenerator.nextIds(5)

      secondIds must containTheSameElementsAs(expectedIds.drop(5))
    }

    "return document ids when document set does not exist" in new DbScope {
      val documentIdGenerator = new TestDocumentIdGenerator(0)
      
      documentIdGenerator.nextIds(5) must haveSize(5)
    }

  }

  trait DocumentSetScope extends DbScope {
    val documentSet = factory.documentSet()
    val expectedIds = Seq.tabulate(10)(n => (documentSet.id << 32 | (n + 1)))

    val documentIdGenerator = new TestDocumentIdGenerator(documentSet.id)
  }

  trait ExistingDocumentsScope extends DocumentSetScope {
    val document = factory.document(id = (documentSet.id << 32) + 1, documentSetId = documentSet.id)
  }

  class TestDocumentIdGenerator(override protected val documentSetId: Long)
    extends DocumentIdGenerator with org.overviewproject.database.BlockingDatabaseProvider

}
