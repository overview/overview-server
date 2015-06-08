package org.overviewproject.test

import java.sql.Connection
import org.postgresql.PGConnection
import org.specs2.execute.AsResult
import org.specs2.mutable.{After,Around}
import org.specs2.specification.{Fragments, Step}
import org.squeryl.{Session=>SquerylSession}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await,Future}
import slick.jdbc.UnmanagedSession
import slick.jdbc.JdbcBackend.Session

import org.overviewproject.database.{BlockingDatabaseProvider, DB, DataSource, DatabaseConfiguration, DatabaseProvider, SlickSessionProvider}
import org.overviewproject.postgres.SquerylPostgreSqlAdapter
import org.overviewproject.postgres.SquerylEntrypoint.using
import org.overviewproject.test.factories.DbFactory

/**
 * Tests that access the database should extend DbSpecification.
 */
class DbSpecification extends Specification {
  sequential

  override def map(fs: => Fragments) = {
    Step(setupDb) ^ super.map(fs) ^ Step(shutdownDb)
  }

  private val DatabaseProperty = "datasource.default.url"
  private val TestDatabase = "postgres://overview:overview@localhost:9010/overview-test"

  /**
   * Context for test accessing the database. All tests are run inside a transaction
   * which is rolled back after the test is complete.
   */
  // Commented out @deprecated because it produces too many warnings.
  //@deprecated("Use DbScope instead: it supports Slick and only connects on-demand", "2015-02-24")
  trait DbTestContext extends Around {
    lazy implicit val connection = DB.getConnection()
    clearDb(connection) // Before around() call

    /** setup method called after database connection is established */
    def setupWithDb {}
    def sql(q: String): Unit = runQuery(q, connection)

    def around[T : AsResult](test: => T) = {
      try {
        val adapter = new SquerylPostgreSqlAdapter()
        val session = new SquerylSession(connection, adapter)
        using(session) { // sets thread-local variable
          setupWithDb
          AsResult(test)
        }
      } finally {
        connection.close()
      }
    }
  }

  /** Context for test accessing the database.
    *
    * Provides these <em>deprecated</em> variables:
    *
    * <ul>
    *   <li><em>connection</em> (lazy): a Connection
    *   <li><em>session</em> (lazy): a Slick Session</li>
    * </ul>
    *
    * Provides these <em>non-deprecated</em> variables:
    *
    * <ul>
    *   <li><em>database</em>: the Database</li>
    *   <li><em>databaseApi</em>: so you can call <tt>import databaseApi._</tt>
    *   <li><em>blockingDatabase</em>: the BlockingDatabase</li>
    *   <li><em>sql</em>: runs arbitrary SQL, returning nothing</li>
    *   <li><em>factory</em>: a DbFactory for constructing objects</li>
    *   <li><em>await</em>: awaits a Future</li>
    * </ul>
    *
    * Whatever code you test with <em>must not commit or start a
    * transaction</em>. When you first use the connection, a transaction will
    * begin; when your test finishes, the transaction will be rolled back.
    */
  trait DbScope extends After with DatabaseProvider with BlockingDatabaseProvider {
    val connection: Connection = DB.getConnection()
    val pgConnection: PGConnection = connection.unwrap(classOf[PGConnection])
    val factory = DbFactory
    lazy implicit val session: Session = new UnmanagedSession(connection)

    def await[A](f: Future[A]) = Await.result(f, Duration.Inf)

    System.setProperty(DatabaseProperty, TestDatabase) // just in case
    val slickDb = SlickSessionProvider.slickDbSingleton
    clearDb(connection) // *not* in a before block: that's too late
    override def after = connection.close()

    def sql(q: String): Unit = runQuery(q, connection)
  }

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

  def setupDb() {
    System.setProperty(DatabaseProperty, TestDatabase)
    val dataSource = DataSource(DatabaseConfiguration.fromConfig)
    DB.connect(dataSource)
  }

  def shutdownDb() {
    DB.close()
  }

}
