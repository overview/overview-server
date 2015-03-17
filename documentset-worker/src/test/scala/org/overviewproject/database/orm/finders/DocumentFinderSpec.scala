package org.overviewproject.database.orm.finders

import org.overviewproject.test.DbSetup.{insertDocumentSet,insertDocuments}
import org.overviewproject.test.DbSpecification

class DocumentFinderSpec extends DbSpecification {
  "DocumentFinder" should {

    trait DocumentSetup extends DbTestContext {
      var documentSetId: Long = _
      var allDocumentIds: Seq[Long] = _

      override def setupWithDb = {
        documentSetId = insertDocumentSet("DocumentFinderSpec")
        allDocumentIds = insertDocuments(documentSetId, 10)
      }
    }

    "find all documents with specified DocumentCloudIds" in new DocumentSetup {
      val documentCloudIds = Seq.tabulate(5)(n => s"documentCloudId-${n + 1}")
      val foundDocuments = DocumentFinder.byDocumentSetAndDocumentCloudIds(documentSetId, documentCloudIds)

      foundDocuments.map(_.id) must containTheSameElementsAs(allDocumentIds.take(5))
    }

    "return empty list if documentCloudIds list is empty" in new DocumentSetup {
      val foundDocuments = DocumentFinder.byDocumentSetAndDocumentCloudIds(documentSetId, Seq.empty).toSeq

      foundDocuments must have size (0)
    }
  }
}
