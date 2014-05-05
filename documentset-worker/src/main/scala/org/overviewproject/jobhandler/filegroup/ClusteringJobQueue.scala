package org.overviewproject.jobhandler.filegroup

import akka.actor.Actor
import akka.actor.ActorRef
import akka.actor.Props

import org.overviewproject.jobhandler.filegroup.ClusteringJobQueueProtocol.ClusterDocumentSet
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol.StartClustering
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

class ClusteringJobQueue(progressReporter: ActorRef) extends Actor {

  def receive = {
    case ClusterDocumentSet(documentSetId) => progressReporter ! StartClustering(documentSetId)
  }
}


object ClusteringJobQueue {

  def apply(progressReporter: ActorRef): Props = Props(new ClusteringJobQueue(progressReporter))
}