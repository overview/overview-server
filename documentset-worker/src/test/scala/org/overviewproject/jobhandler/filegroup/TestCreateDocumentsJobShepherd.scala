package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import org.specs2.mock.Mockito
import org.overviewproject.jobhandler.filegroup.task.UploadProcessOptions



class TestCreateDocumentsJobShepherd(
    val documentSetId: Long,
    val fileGroupId: Long, 
    val options: UploadProcessOptions,
    val taskQueue: ActorRef,
    val progressReporter: ActorRef,
    uploadedFileIds: Set[Long]) extends CreateDocumentsJobShepherd with Mockito {
  override protected val storage = smartMock[Storage]
  
  storage.uploadedFileIds(fileGroupId) returns uploadedFileIds.toSet
  storage.processedFileCount(documentSetId) returns uploadedFileIds.size
}
