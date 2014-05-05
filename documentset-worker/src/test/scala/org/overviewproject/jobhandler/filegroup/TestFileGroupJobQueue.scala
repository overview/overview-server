package org.overviewproject.jobhandler.filegroup

import akka.actor.Props
import akka.actor.ActorRef

class TestFileGroupJobQueue(
    tasks: Seq[Long],
    override protected val progressReporter: ActorRef) extends FileGroupJobQueue {

  class TestStorage extends Storage {
    override def uploadedFileIds(fileGroupId: Long): Set[Long] = tasks.toSet
  }

  override protected val storage = new TestStorage
}

object TestFileGroupJobQueue {
  def apply(tasks: Seq[Long], progressReporter: ActorRef): Props =
    Props(new TestFileGroupJobQueue(tasks, progressReporter))
}

