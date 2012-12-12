/*
 * DocumentWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import org.overviewproject.test.DbSetup.insertDocumentSet
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.DocumentType.{CsvImportDocument, DocumentCloudDocument}
import org.squeryl.PrimitiveTypeMode._

import persistence.Schema.documents

class DocumentWriterSpec extends DbSpecification {

  step(setupDb)

  "DocumentWriter" should {

    trait Setup extends DbTestContext {
      var documentSetId: Long = _

      override def setupWithDb = {
        documentSetId = insertDocumentSet("DocumentWriterSpec")
      }
    }

    "update description of document" in new Setup {
      import persistence.Schema.documents
      
      val title = "title"
      val documentCloudId = Some("documentCloud-id")
      val description = "some,terms,together"

      val document = Document(DocumentCloudDocument, documentSetId, documentcloudId = documentCloudId)
      DocumentWriter.write(document)
      DocumentWriter.updateDescription(document.id, description)

      val updatedDocument = documents.lookup(document.id) 

      updatedDocument must beSome
      updatedDocument.get.title must be equalTo description 

    }

    "write a document cloud document" in new Setup {
      val document = Document(DocumentCloudDocument, documentSetId, documentcloudId = Some("dcId"))
      DocumentWriter.write(document)

      document.id must not be equalTo(0)
    }

    "write a csv import document" in new Setup {
      val document = Document(CsvImportDocument, documentSetId, text = Some("text"), url = None)
      DocumentWriter.write(document)

      document.id must not be equalTo(0)
    }
  }

  step(shutdownDb)
}
