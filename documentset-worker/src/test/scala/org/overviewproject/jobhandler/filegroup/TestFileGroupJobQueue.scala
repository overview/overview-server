package org.overviewproject.jobhandler.filegroup

import akka.actor.Props
import akka.actor.ActorRef

class TestFileGroupJobQueue(
    tasks: Seq[Long],
    override protected val progressReporter: ActorRef) extends FileGroupJobQueue {

  override protected val jobShepherdFactory = new TestJobShepherdFactory
  
  class TestJobShepherdFactory extends JobShepherdFactory {
    override def createShepherd(documentSetId: Long, job: FileGroupJob, taskQueue: ActorRef, progressReporter: ActorRef): JobShepherd = job match {
      case CreateDocumentsJob(fileGroupId, splitDocuments) =>
        new TestCreateDocumentsJobShepherd(documentSetId, fileGroupId, splitDocuments, taskQueue, progressReporter, tasks.toSet)
      case DeleteFileGroupJob(fileGroupId) => new DeleteFileGroupJobShepherd(documentSetId, fileGroupId, taskQueue)
    }
    
  }
}

object TestFileGroupJobQueue {
  def apply(tasks: Seq[Long], progressReporter: ActorRef): Props =
    Props(new TestFileGroupJobQueue(tasks, progressReporter))
}


