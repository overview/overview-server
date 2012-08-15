package database

import anorm._
import anorm.SqlParser._
import helpers.DbSpecification
import org.specs2.execute.Result
import org.specs2.mutable.Around
import org.specs2.mutable.Specification

class DBSpec extends DbSpecification {
  step(setupDB) 
  
  "DB object" should {

    "provide scope with connection" in {
      DB.withConnection { implicit connection =>
        val success = SQL("SELECT * from document_set").execute
        success must beTrue
      }
    }

    "provide scope with transaction" in  {
      DB.withTransaction { implicit connection =>
        SQL("""
             INSERT INTO document_set (id, query) 
         	 VALUES (nextval('document_set_seq'), 'q')
             """).executeInsert()

        connection.rollback()
      }

      DB.withConnection { implicit connection =>
        val id = SQL("SELECT id FROM document_set").as(long("id") singleOpt)
        id must beNone
      }
    }
    
    "rollback transaction on exception" in {
      val exceptionMessage = "trigger rollback"
        
      try {
        DB.withTransaction { implicit connection =>
          SQL("""
            INSERT INTO document_set (id, query) 
        	VALUES (nextval('document_set_seq'), 'q')
            """).executeInsert()

          throw new Exception(exceptionMessage)
        }
      }
      catch {
        case e => e.getMessage must be equalTo(exceptionMessage) 
      }

      DB.withConnection { implicit connection =>
        val id = SQL("SELECT id FROM document_set").as(long("id") singleOpt)  
        id must beNone
      }
    }
  }
  
  step(shutdownDB)
}