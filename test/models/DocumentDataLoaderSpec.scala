package models

import anorm._
import anorm.SqlParser._
import helpers.{DbSetup,DbTestContext}
import java.sql.Connection
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import play.api.Play.{ start, stop }

class DocumentDataLoaderSpec extends Specification {

  step(start(FakeApplication()))
  
  "DocumentDataLoader" should {
    
    "Load document data for specified id" in new DbTestContext {
      val documentSetId = DbSetup.insertDocumentSet("DocumentDataLoaderSpec")
      val insertedDocumentId = DbSetup.insertDocument(documentSetId, "title", "documentCloudId")

      val documentDataLoader = new DocumentDataLoader()
      val document = documentDataLoader.loadDocument(insertedDocumentId)

      document must beSome
      document.get must be equalTo((insertedDocumentId, "title", "documentCloudId"))
    }
    
    "Returns None for non-existing id" in new DbTestContext {
      val documentDataLoader = new DocumentDataLoader()

      documentDataLoader.loadDocument(-1) must beNone
    }
  }
  
  step(stop)
}
