package org.overviewproject.jobhandler.filegroup

import akka.actor.ActorRef
import org.specs2.mock.Mockito



class TestCreateDocumentsJobShepherd(
    val documentSetId: Long,
    val fileGroupId: Long, 
    val splitDocuments: Boolean,
    val taskQueue: ActorRef,
    val progressReporter: ActorRef,
    uploadedFileIds: Set[Long]) extends CreateDocumentsJobShepherd with Mockito {
  override protected val storage = smartMock[Storage]
  
  storage.uploadedFileIds(fileGroupId) returns uploadedFileIds.toSet
  storage.processedFileCount(documentSetId) returns uploadedFileIds.size
}
