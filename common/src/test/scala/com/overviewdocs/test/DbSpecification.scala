package com.overviewdocs.test

import org.specs2.mutable.{After,Around,Specification}
import org.specs2.specification.Scope
import scala.concurrent.duration.Duration
import scala.concurrent.{Await,Future,blocking}

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.test.factories.{DbFactory,PodoFactory}
import com.overviewdocs.util.AwaitMethod

/**
 * Tests that access the database should extend DbSpecification.
 */
class DbSpecification extends Specification with AwaitMethod {
  sequential

  /** Context for test accessing the database.
    *
    * Provides these variables:
    *
    * <ul>
    *   <li><em>database</em>: the Database</li>
    *   <li><em>blockingDatabase</em>: the BlockingDatabase</li>
    *   <li><em>factory</em>: a DbFactory for constructing objects</li>
    *   <li><em>podoFactory</em>: a PodoFactory for constructing objects</li>
    *   <li><em>await</em>: awaits a Future</li>
    * </ul>
    *
    * You must only run one test at a time: the database is a global variable.
    */
  trait DbScope extends Scope with HasBlockingDatabase {
    val factory = DbFactory
    val podoFactory = PodoFactory

    clearDb // Not in a Before block: that's too late

    private def clearDb: Unit = {
      import database.api._
      blockingDatabase.runUnit(sqlu"""
        WITH
        q1 AS (DELETE FROM document_store_object),
        q1_i_remember_BASIC_now AS (DELETE FROM dangling_node),
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
        q20 AS (DELETE FROM document_cloud_import_id_list),
        q21 AS (DELETE FROM document_cloud_import),
        q22 AS (DELETE FROM api_token),
        q23 AS (DELETE FROM plugin),
        q24 AS (DELETE FROM "session"),
        q25 AS (DELETE FROM "user"),
        q26 AS (DELETE FROM csv_import),
        q27 AS (DELETE FROM clone_job),
        q28 AS (DELETE FROM document_set_reindex_job)
        DELETE FROM document_set
      """)
    }
  }
}
