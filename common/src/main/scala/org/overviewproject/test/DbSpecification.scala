/*
 * DbSpecification.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, Aug 2012
 */

package org.overviewproject.test

import org.specs2.execute.Result
import org.specs2.mutable.Around
import overview.database.{ DataSource, DatabaseConfiguration, DB }
import org.junit.runner.RunWith
import org.specs2.runner.JUnitRunner

/**
 * Tests that access the database should extend DbSpecification.
 * Before any examples, call step(setupDB), and after examples call step(shutdownDB).
 */
class DbSpecification extends Specification {
  private val DatabaseProperty = "datasource.default.url"
  private val TestDatabase = "postgres://overview:overview@localhost/overview-test"		
      
  /**
   * Context for test accessing the database. All tests are run inside a transaction
   * which is rolled back after the test is complete.
   */
  trait DbTestContext extends Around {
    lazy implicit val connection = DB.getConnection()

    /** setup method called after database connection is established */
    def setupWithDb {}

    def around[T <% Result](test: => T) = {
      try {
        connection.setAutoCommit(false)
        setupWithDb
        test
      } finally {
        connection.rollback()
        connection.close()
      }
    }
  }

  def setupDb() {
    System.setProperty(DatabaseProperty, TestDatabase)
    val dataSource = new DataSource(new DatabaseConfiguration)
    DB.connect(dataSource)
  }

  def shutdownDb() {
    DB.close()
  }

}
