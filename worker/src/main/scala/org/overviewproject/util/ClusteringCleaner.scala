package org.overviewproject.util

import scala.concurrent.Future
import org.overviewproject.models.DocumentSetCreationJob
import org.overviewproject.database.SlickClient
import scala.slick.jdbc.StaticQuery.interpolation
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentSetCreationJobState
import org.overviewproject.models.tables.{ DocumentSetCreationJobs, DocumentSetCreationJobNodes, NodeDocuments, Nodes, Trees }

trait ClusteringCleaner extends SlickClient {



  def updateValidJob(job: DocumentSetCreationJob): Future[Unit] = db { implicit session =>
    val updatedJob = sqlu"""
          WITH ids AS (
            SELECT id FROM document_set_creation_job
            WHERE state <> ${DocumentSetCreationJobState.Cancelled.id}
          )
          UPDATE document_set_creation_job
          SET state = ${job.state.id},
              retry_attempts = ${job.retryAttempts},
              status_description = ${job.statusDescription}
          WHERE id IN (SELECT id FROM ids)    
        """
    updatedJob.execute
  }
  
  def treeExists(jobId: Long): Future[Boolean] = db { implicit session => 
    Trees.filter(_.jobId === jobId).firstOption.isDefined
  }
 
  def deleteNodes(jobId: Long): Future[Unit] = db { implicit session =>
     val documentSetCreationJobNodes = DocumentSetCreationJobNodes.filter(_.documentSetCreationJobId === jobId)
     val rootNode = documentSetCreationJobNodes.map(_.nodeId).firstOption
     
     val nodes = Nodes.filter(_.rootId === rootNode)
     val nodeDocuments = NodeDocuments.filter(_.nodeId in nodes.map(_.id))
     
     documentSetCreationJobNodes.delete
     nodeDocuments.delete
     nodes.delete
  }
  
  def deleteDocumentSetCreationJobNode(jobId: Long): Future[Unit] = db { implicit session =>
    val documentSetCreationJobNodes = DocumentSetCreationJobNodes.filter(_.documentSetCreationJobId === jobId)
    
    documentSetCreationJobNodes.delete
  }
}