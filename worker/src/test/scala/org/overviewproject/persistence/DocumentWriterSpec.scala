/*
 * DocumentWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.persistence

import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.{ Document, DocumentSet }
import org.overviewproject.persistence.orm.Schema.{ documents, documentSets }
import org.overviewproject.postgres.SquerylEntrypoint._

class DocumentWriterSpec extends DbSpecification {

  step(setupDb)

  "DocumentWriter" should {

    trait Setup extends DbTestContext {
      var documentSet: DocumentSet = _
      var ids: DocumentSetIdGenerator = _
      
      override def setupWithDb = {
        documentSet = documentSets.insert(DocumentSet(title = "DocumentWriterSpec"))
        ids = new DocumentSetIdGenerator(documentSet.id)
      }
    }

    "update description of document" in new Setup {
      import org.overviewproject.persistence.orm.Schema.documents

      val documentCloudId = Some("documentCloud-id")
      val description = "some,terms,together"

      val document = Document(documentSet.id, documentcloudId = documentCloudId, id = ids.next)
      DocumentWriter.write(document)
      DocumentWriter.updateDescription(document.id, description)

      val updatedDocument = documents.lookup(document.id)

      updatedDocument must beSome
      updatedDocument.get.description must be equalTo description

    }

    "write a document cloud document" in new Setup {
      val document = Document(documentSet.id, documentcloudId = Some("dcId"), id = ids.next)
      DocumentWriter.write(document)

      document.id must not be equalTo(0)
    }

    "write a csv import document" in new Setup {
      val document = Document(documentSet.id, text = Some("text"), url = None, id = ids.next)
      DocumentWriter.write(document)

      document.id must not be equalTo(0)
    }
  }

  step(shutdownDb)
}
