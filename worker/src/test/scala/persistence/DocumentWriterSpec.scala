/*
 * DocumentWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */


package persistence

import anorm._
import anorm.SqlParser._
import helpers.{DbSpecification, DbTestContext}
import org.specs2.mutable.Specification

class DocumentWriterSpec extends DbSpecification {

  step(setupDB)
  
  "DocumentWriter" should {
    
    "write title, text_url, and view_url to document table" in new DbTestContext {
      val documentSetId = 
        SQL("""
            INSERT INTO document_set (id, query)
            VALUES (nextval('document_set_seq'), 'DocumentWriterSpec')
        	""").executeInsert().getOrElse(throw new Exception("Failed insert"))
        	
      val writer = new DocumentWriter(documentSetId)
      val title = "title"
      val textUrl = "textUrl"
      val viewUrl = "viewUrl"
        
      val id = writer.write(title, textUrl, viewUrl)
      val documents = 
        SQL("SELECT id, title, text_url, view_url FROM document").
          as(long("id") ~ str("title") ~ str("text_url") ~ str("view_url") map(flatten) *)
          
      documents must haveTheSameElementsAs(Seq((id, title, textUrl, viewUrl)))
      
    }
  }
  
  step(shutdownDB)
}