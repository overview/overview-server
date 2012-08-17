/*
 * DocumentSetWriterSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package persistence

import anorm._
import anorm.SqlParser._
import helpers.{DbSpecification, DbTestContext}
import org.specs2.mutable.Specification


class DocumentSetWriterSpec extends DbSpecification {

  step(setupDB)
  
  "DocumentSetWriter" should {
    
    "write query into document_set table and return id" in new DbTestContext {
      val adminUserId = 1l;
      val writer = new DocumentSetWriter(adminUserId)
      val query = "a query"
        
      val id = writer.write(query)
      
      val savedIds = SQL("SELECT id, query FROM document_set").
       					as(long("id") ~ str("query") map(flatten) *)  
          
      savedIds must haveTheSameElementsAs(Seq((id, query)))
      
      val documentSetUsers = SQL("SELECT document_set_id, user_id FROM document_set_user").
                                as(long("document_set_id") ~ long("user_id") map(flatten) *)
                                
      documentSetUsers.distinct must contain((id, adminUserId)).only
    }
  }
  
  step(shutdownDB)
}