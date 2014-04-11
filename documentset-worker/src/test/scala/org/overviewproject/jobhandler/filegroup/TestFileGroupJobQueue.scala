package org.overviewproject.jobhandler.filegroup

class TestFileGroupJobQueue(tasks: Seq[Long]) extends FileGroupJobQueue {

  class TestStorage extends Storage {
    override def uploadedFileIds(fileGroupId: Long): Iterable[Long] = tasks
  }

  override protected val storage = new TestStorage
}

