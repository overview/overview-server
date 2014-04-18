package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor
import org.overviewproject.jobhandler.filegroup.ClusteringJobQueueProtocol.ClusterDocumentSet

trait ClusteringJobQueue extends Actor {
	
  protected val storage: Storage
  
  trait Storage {
    def submitJob(documentSetId: Long)
  }
  
  def receive = {
    case ClusterDocumentSet(documentSetId) => storage.submitJob(documentSetId)
  }
}