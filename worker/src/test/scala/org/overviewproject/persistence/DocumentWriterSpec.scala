/*
 * DocumentWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */
package org.overviewproject.persistence

import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.test.DbSpecification

class DocumentWriterSpec extends DbSpecification {
  "DocumentWriter" should {
    "update description of document" in new DbScope {
      val documentSet = factory.documentSet()
      val document = factory.document(documentSetId=documentSet.id, keywords=Seq("foo", "bar"))

      DeprecatedDatabase.inTransaction {
        DocumentWriter.updateDescription(document.id, "bar baz")
      }

      import databaseApi._
      import org.overviewproject.models.tables.Documents
      blockingDatabase.option(Documents.filter(_.id === document.id)).map(_.keywords) must beSome(Seq("bar", "baz"))
    }
  }
}
