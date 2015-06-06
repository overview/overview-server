/*
 * DocumentWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */
package org.overviewproject.persistence

import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.test.DbSpecification
import org.overviewproject.postgres.SquerylEntrypoint._

class DocumentWriterSpec extends DbSpecification {
  "DocumentWriter" should {
    "update description of document" in new DbScope {
      val documentSet = factory.documentSet()
      val document = factory.document(documentSetId=documentSet.id, keywords=Seq("foo", "bar"))

      DeprecatedDatabase.inTransaction {
        DocumentWriter.updateDescription(document.id, "bar baz")
      }

      import org.overviewproject.database.Slick.api._
      import org.overviewproject.models.tables.Documents
      slickDb.run(Documents.filter(_.id === document.id).result.head)
        .map(_.keywords) must beEqualTo(Seq("bar", "baz")).await
    }
  }
}
