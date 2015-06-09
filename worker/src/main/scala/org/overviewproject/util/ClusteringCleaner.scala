package org.overviewproject.util

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.database.DatabaseProvider
import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.models.DocumentSetCreationJobState
import org.overviewproject.models.tables.{ DocumentSetCreationJobs, DocumentSetCreationJobNodes, NodeDocuments, Nodes, Trees }

/**
 * Provides methods to cleanup an interrupted clustering, prior to restart.
 */
trait ClusteringCleaner extends JobUpdater with DatabaseProvider {
  import databaseApi._
  
  def treeExists(jobId: Long): Future[Boolean] = {
    database.option(Trees.filter(_.jobId === jobId)).map(_.isDefined)
  }

  def deleteNodes(jobId: Long): Future[Unit] = {
    // Ensure we don't end up with partially deleted/updated pieces by using a
    // single SQL statement.
    database.runUnit(sqlu"""
      WITH root_node AS (
        DELETE FROM document_set_creation_job_node WHERE document_set_creation_job_id = $jobId
        RETURNING node_id
      ),
      nodes AS (
        SELECT id FROM node WHERE root_id IN (SELECT node_id FROM root_node)
      ),
      nd_delete AS (
        DELETE FROM node_document WHERE node_id IN (SELECT id FROM nodes)
        RETURNING node_id
      )
      DELETE FROM node WHERE id IN (SELECT id FROM nodes) 
    """)
  }

  def deleteJob(jobId: Long): Future[Unit] = {
    database.run(for {
      _ <- DocumentSetCreationJobNodes.filter(_.documentSetCreationJobId === jobId).delete
      _ <- DocumentSetCreationJobs.filter(_.id === jobId).delete
    } yield ())
  }
}
