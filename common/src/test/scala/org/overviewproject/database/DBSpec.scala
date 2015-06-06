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
import java.sql.{ Connection, SQLException }

class DBSpec extends DbSpecification {
  private def insertFileGroup(implicit connection: Connection): Unit =
    SQL("""
          INSERT INTO file_group (user_email, api_token, completed, deleted)
          VALUES ('user@host', NULL, false, false)
        """).executeInsert()

  "DB object" should {

    "provide scope with connection" in {
      DB.withConnection { implicit connection =>
        val success = SQL("SELECT * from document_set").execute
        success must beTrue
      }
    }

    "provide scope with transaction" in {
      DB.withTransaction { implicit connection =>
        insertFileGroup
        connection.rollback()
      }

      DB.withConnection { implicit connection =>
        val email = SQL("""SELECT user_email FROM file_group""").as(str("user_email") singleOpt)
        email must beNone
      }
    }

    "provide PGConnection" in {
      DB.withConnection { implicit connection =>
        DB.pgConnection.getLargeObjectAPI() must not(throwA[SQLException])
      }
    }
  }
}
