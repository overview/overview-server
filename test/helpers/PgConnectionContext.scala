package helpers

import java.sql.Connection
import org.postgresql.PGConnection
import org.specs2.execute.AsResult
import org.specs2.mutable.Around
import org.squeryl.Session
import play.api.db.DB
import play.api.Play.current

import org.overviewproject.postgres.SquerylEntrypoint.using
import org.overviewproject.postgres.SquerylPostgreSqlAdapter

// We can't use DbTestContext, because it uses a connection
// returned from DB.withConnection, which is an AutoCleanConnection wrapper
// around bonecp.ConnectionHandle
//
// FIXME Yes We Can! We use HikariCP now, which supports
// java.sql.Connection#unwrap()
trait PgConnectionContext extends Around {
  import scala.language.implicitConversions
  
  implicit var pgConnection: PGConnection = _
  lazy implicit val connection = Session.currentSession.connection
  
  def setupWithDb = {}

  def around[T : AsResult](test: => T) = {
    // DB.getConnection returns an unwrapped bonecp.ConnectionHandle
    // hopefully we are not leaking statements, but as long as we
    // just use the LargeObjectAPI, we should be ok...
    val connection = DB.getConnection()
    try {
      val adapter = new SquerylPostgreSqlAdapter()
      val session = new Session(connection, adapter)

      pgConnection = connection.unwrap(classOf[PGConnection])

      using(session) {
        clearDb(connection)
        setupWithDb
        AsResult(test)
      }
    } finally {
      connection.close()
    }
  }

  // XXX remove this entire file. It copy/pastes from DbSpecification.sacla.
  private def runQuery(query: String, connection: Connection): Unit = {
    val st = connection.createStatement()
    try {
      st.execute(query)
    } finally {
      st.close()
    }
  }

  private def clearDb(connection: Connection) = {
    runQuery("""
      WITH q1 AS (DELETE FROM document_store_object),
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
