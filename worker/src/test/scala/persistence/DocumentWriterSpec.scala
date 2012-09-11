/*
 * DocumentWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */


package persistence

import anorm._
import anorm.SqlParser._
import helpers.DbSpecification
import helpers.DbSetup._
import org.specs2.mutable.Specification

class DocumentWriterSpec extends DbSpecification {

  step(setupDb)
  
  "DocumentWriter" should {
    
    "write title and document_cloud_id to document table" in new DbTestContext {
      val documentSetId = insertDocumentSet("DocumentWriterSpec")
        	
      val writer = new DocumentWriter(documentSetId)
      val title = "title"
      val documentCloudId = "documentCloud-id"
        
      val id = writer.write(title, documentCloudId)
      val documents = 
        SQL("SELECT id, title, documentcloud_id FROM document").
          as(long("id") ~ str("title") ~ str("documentcloud_id") map(flatten) *)
          
      documents must haveTheSameElementsAs(Seq((id, title, documentCloudId)))
      
    }
  }
  
  step(shutdownDb)
}
