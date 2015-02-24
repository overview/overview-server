package org.overviewproject.test

import java.sql.Connection
import org.specs2.execute.AsResult
import org.specs2.mutable.{After,Around}
import org.specs2.specification.{Fragments, Step}
import org.squeryl.{Session=>SquerylSession}
import scala.concurrent.duration.Duration
import scala.concurrent.{Await,Future}
import scala.slick.jdbc.UnmanagedSession
import scala.slick.jdbc.JdbcBackend.Session

import org.overviewproject.database.{DB, DataSource, DatabaseConfiguration}
import org.overviewproject.postgres.SquerylPostgreSqlAdapter
import org.overviewproject.postgres.SquerylEntrypoint.using

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

    /** setup method called after database connection is established */
    def setupWithDb {}

    def around[T : AsResult](test: => T) = {
      try {
        connection.setAutoCommit(false)
        val adapter = new SquerylPostgreSqlAdapter()
        val session = new SquerylSession(connection, adapter)
        using(session) { // sets thread-local variable
          setupWithDb
          AsResult(test)
        }
      } finally {
        connection.rollback()
        connection.close()
      }
    }
  }

  /** Context for test accessing the database.
    *
    * Provides these variables:
    *
    * <ul>
    *   <li><em>connection</em> (lazy): a Connection
    *   <li><em>session</em> (lazy): a Slick Session</li>
    *   <li><em>await</em>: awaits a Future</li>
    *   <li><em>sql</em>: runs arbitrary SQL, returning nothing</li>
    * </ul>
    *
    * Whatever code you test with <em>must not commit or start a
    * transaction</em>. When you first use the connection, a transaction will
    * begin; when your test finishes, the transaction will be rolled back.
    */
  trait DbScope extends After {
    private var connected = false
    lazy val connection: Connection = {
      connected = true
      val ret = DB.getConnection()
      ret.setAutoCommit(false)
      ret
    }
    lazy implicit val session: Session = new UnmanagedSession(connection)

    def await[A](f: Future[A]) = Await.result(f, Duration.Inf)

    override def after = {
      if (connected) {
        connection.rollback()
        connection.close()
      }
    }

    def sql(q: String): Unit = session.withPreparedStatement(q) { (st) => st.execute }
  }

  def setupDb() {
    System.setProperty(DatabaseProperty, TestDatabase)
    val dataSource = DataSource(DatabaseConfiguration.fromSystemProperties)
    DB.connect(dataSource)
  }

  def shutdownDb() {
    DB.close()
  }

}
