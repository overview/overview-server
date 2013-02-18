/*
 * DBSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.database

import anorm._
import anorm.SqlParser._
import org.overviewproject.test.DbSpecification
import org.overviewproject.test.DbSetup._
import java.sql.{ Connection, SQLException }

class DBSpec extends DbSpecification {
  step(setupDb)

  "DB object" should {

    "provide scope with connection" in {
      DB.withConnection { implicit connection =>
        val success = SQL("SELECT * from document_set").execute
        success must beTrue
      }
    }

    "provide scope with transaction" in {
      DB.withTransaction { implicit connection =>
        insertDocumentSet("DBSpec")
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
          insertDocumentSet("DBSpec")
          throw new Exception(exceptionMessage)
        }
      } catch {
        case e => e.getMessage must be equalTo (exceptionMessage)
      }

      DB.withConnection { implicit connection =>
        val id = SQL("SELECT id FROM document_set").as(long("id") singleOpt)
        id must beNone
      }
    }

    "provide PGConnection" in {
      DB.withConnection { implicit connection =>
        DB.pgConnection.getLargeObjectAPI() must not(throwA[SQLException])
      }
    }
  }

  step(shutdownDb)
}
