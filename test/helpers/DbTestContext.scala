/*
 * DbTestContext.scala 
 * 
 * Overview Project
 * Created by Jonas Karlsson, July 2012
 */

package helpers

import java.sql.Connection
import org.specs2.execute.AsResult
import org.specs2.mutable.Around
import org.squeryl.Session
import org.squeryl.PrimitiveTypeMode.using
import play.api.db.DB
import play.api.Play.current
import play.api.test._
import play.api.test.Helpers._

import models.OverviewDatabase

/**
 * A helper class for tests that access the test-database. Wraps the test in a
 * transaction.
 * The database connection is available as an implicit parameter. 
 */
trait DbTestContext extends Around {
  lazy implicit val connection = Session.currentSession.connection

  def setupWithDb = {}
  def sql(q: String) = runQuery(q, connection)
  
  def around[T : AsResult](test: => T) = {
    OverviewDatabase.inTransaction {
      clearDb(connection)
      setupWithDb
      AsResult(test)
    }
  }

  private def runQuery(query: String, connection: Connection): Unit = {
    val st = connection.createStatement()
    try {
      st.execute(query)
    } finally {
      st.close()
    }
  }

  // ICK copied from DbSpecification.scala
  // (This whole class needs to be deleted anyway.)
  private def clearDb(connection: Connection) = {
    runQuery("""
      WITH
      q1 AS (DELETE FROM document_store_object),
      q1_i_remember_basic_now AS (DELETE FROM document_set_creation_job_node),
      q2 AS (DELETE FROM store_object),
      q3 AS (DELETE FROM store),
      q4 AS (DELETE FROM temp_document_set_file),
      q5 AS (DELETE FROM node_document),
      q6 AS (DELETE FROM tree),
      q7 AS (DELETE FROM node),
      q8 AS (DELETE FROM document_tag),
      q9 AS (DELETE FROM tag),
      q10 AS (DELETE FROM file),
      q11 AS (DELETE FROM grouped_file_upload),
      q12 AS (DELETE FROM file_group),
      q13 AS (DELETE FROM page),
      q14 AS (DELETE FROM document),
      q15 AS (DELETE FROM uploaded_file),
      q16 AS (DELETE FROM upload),
      q17 AS (DELETE FROM "view"),
      q18 AS (DELETE FROM document_set_user),
      q19 AS (DELETE FROM document_processing_error),
      q20 AS (DELETE FROM document_set_creation_job),
      q21 AS (DELETE FROM api_token),
      q22 AS (DELETE FROM plugin),
      q23 AS (DELETE FROM "session"),
      q24 AS (DELETE FROM "user"),
      q25 AS (DELETE FROM document_set)
      SELECT 1;
    """, connection)
  }
}
