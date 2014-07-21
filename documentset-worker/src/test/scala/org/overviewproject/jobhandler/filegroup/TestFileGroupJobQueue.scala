package org.overviewproject.jobhandler.filegroup

import akka.actor.Props
import akka.actor.ActorRef

class TestFileGroupJobQueue(
    tasks: Seq[Long],
    override protected val progressReporter: ActorRef) extends FileGroupJobQueue {

  override protected val jobTrackerFactory = new TestJobTrackerFactory
  
  class TestJobTrackerFactory extends JobTrackerFactory {
    override def createTracker(documentSetId: Long, job: FileGroupJob, taskQueue: ActorRef): JobTracker = 
      new TestCreateDocumentsJobTracker(documentSetId, job.fileGroupId, taskQueue, tasks)     
    
  }
}

object TestFileGroupJobQueue {
  def apply(tasks: Seq[Long], progressReporter: ActorRef): Props =
    Props(new TestFileGroupJobQueue(tasks, progressReporter))
}


class TestCreateDocumentsJobTracker(val documentSetId: Long, val fileGroupId: Long, val taskQueue: ActorRef, tasks: Seq[Long]) extends CreateDocumentsJobTracker {
  
  override protected val storage = new TestStorage
  
  class TestStorage extends Storage {
    override def uploadedFileIds(fileGroupId: Long): Set[Long] = tasks.toSet
  }
  
}