package com.overviewdocs.util

import slick.jdbc.JdbcBackend.Session

import com.overviewdocs.test.DbSpecification
import com.overviewdocs.models.tables.Documents

class DocumentSetCleanerSpec extends DbSpecification {
  "DocumentSetCleaner" should {
    "delete documents" in new DocumentSetScope {
      await(cleaner.deleteDocuments(documentSet.id))

      import database.api._
      blockingDatabase.length(Documents.filter(_.documentSetId === documentSet.id)) must beEqualTo(0)
    }
  }

  trait DocumentSetScope extends DbScope {
    val cleaner = DocumentSetCleaner
    val documentSet = factory.documentSet()
    val document = factory.document(documentSetId = documentSet.id)
  }
}
