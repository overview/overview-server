/*
 * DocumentWriterSpec.scala
 *
 * Overview
 * Created by Jonas Karlsson, Aug 2012
 */
package com.overviewdocs.persistence

import com.overviewdocs.database.DeprecatedDatabase
import com.overviewdocs.test.DbSpecification

class DocumentWriterSpec extends DbSpecification {
  "DocumentWriter" should {
    "update description of document" in new DbScope {
      val documentSet = factory.documentSet()
      val document = factory.document(documentSetId=documentSet.id, keywords=Seq("foo", "bar"))

      DocumentWriter.updateDescription(document.id, "bar baz")

      import database.api._
      import com.overviewdocs.models.tables.Documents
      blockingDatabase.option(Documents.filter(_.id === document.id)).map(_.keywords) must beSome(Seq("bar", "baz"))
    }
  }
}
