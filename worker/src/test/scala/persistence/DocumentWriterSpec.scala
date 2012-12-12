/*
 * DocumentWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import anorm.SqlParser._
import org.overviewproject.test.DbSpecification
import org.specs2.mutable.Specification
import org.overviewproject.test.DbSetup._
import org.overviewproject.tree.orm.DocumentType._
import org.overviewproject.tree.orm.Document

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
      val title = "title"
      val documentCloudId = "documentCloud-id"
      val description = "some,terms,together"

      val id = insertDocument(documentSetId, title, documentCloudId)
      DocumentWriter.updateDescription(id, description)

      val documents =
        SQL("SELECT id, title, documentcloud_id FROM document").
          as(long("id") ~ str("title") ~ str("documentcloud_id") map (flatten) *)

      documents must haveTheSameElementsAs(Seq((id, description, documentCloudId)))

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
