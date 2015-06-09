package org.overviewproject.util

import slick.jdbc.JdbcBackend.Session

import org.overviewproject.test.DbSpecification
import org.overviewproject.models.tables.Documents

class DocumentSetCleanerSpec extends DbSpecification {
  "DocumentSetCleaner" should {
    "delete documents" in new DocumentSetScope {
      await(cleaner.deleteDocuments(documentSet.id))

      import databaseApi._
      blockingDatabase.length(Documents.filter(_.documentSetId === documentSet.id)) must beEqualTo(0)
    }
  }

  trait DocumentSetScope extends DbScope {
    val cleaner = new DocumentSetCleaner with org.overviewproject.database.DatabaseProvider {}
    val documentSet = factory.documentSet()
    val document = factory.document(documentSetId = documentSet.id)
  }
}
