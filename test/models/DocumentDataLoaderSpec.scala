package models

import anorm._
import anorm.SqlParser._
import helpers.DbTestContext
import java.sql.Connection
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }

class DocumentDataLoaderSpec extends Specification {

  step(start(FakeApplication()))
  
  "DocumentDataLoader" should {
    
    "Load document data for speficied id" in new DbTestContext {
      val documentSetId = 
        SQL("""
            INSERT INTO document_set (id, query)
            VALUES (nextval('document_set_seq'), 'DocumentDataLoaderSpec')
            """).executeInsert()
            
      val insertedDocumentId = 
        SQL("""
            INSERT INTO document (id, title, text_url, view_url, document_set_id)
            VALUES (nextval('document_seq'), 'title', 'textUrl', 'viewUrl', {documentSetId})
            """).on("documentSetId" -> documentSetId).executeInsert()

      insertedDocumentId must beSome
      
      val documentId = insertedDocumentId.get
      
      val documentDataLoader = new DocumentDataLoader()
      
      val document = documentDataLoader.loadDocument(documentId)
      
      document must be equalTo((documentId, "title", "textUrl", "viewUrl"))
    }
  }
  
  step(stop)
}