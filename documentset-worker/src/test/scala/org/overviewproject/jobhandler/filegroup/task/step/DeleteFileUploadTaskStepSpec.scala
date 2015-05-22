package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Promise
import org.overviewproject.database.DocumentSetCreationJobDeleter
import org.overviewproject.database.DocumentSetDeleter
import org.overviewproject.database.FileGroupDeleter
import org.overviewproject.database.TempFileDeleter
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import scala.concurrent.Future


class DeleteFileUploadTaskStepSpec extends Specification with Mockito with NoTimeConversions {

  "DeleteFileUploadTaskStep" should {

    "complete when all deleters complete" in new FileUploadScope {
      val result = step.execute

      result must beEqualTo(FinalStep).await
    }

    "delete job before starting other deleters" in new FileUploadScope {
      implicit val order = inOrder(
          mockJobDeleter,
          mockTempFileDeleter,
          mockDocumentSetDeleter,
          mockFileGroupDeleter)
          
      val result = step.execute


      there was one(mockJobDeleter).deleteByDocumentSet(documentSetId) andThen
      one(mockTempFileDeleter).delete(documentSetId) andThen
      one(mockDocumentSetDeleter).delete(documentSetId) andThen
      one(mockFileGroupDeleter).delete(fileGroupId)
    }

  }

  trait FileUploadScope extends Scope {
    val documentSetId = 1l
    val fileGroupId = 13l

    val mockJobDeleter = smartMock[DocumentSetCreationJobDeleter]
    val mockDocumentSetDeleter = smartMock[DocumentSetDeleter]
    val mockFileGroupDeleter = smartMock[FileGroupDeleter]
    val mockTempFileDeleter = smartMock[TempFileDeleter]

    def success = Future.successful(())
    
    mockJobDeleter.deleteByDocumentSet(documentSetId) returns success
    mockDocumentSetDeleter.delete(documentSetId) returns success
    mockFileGroupDeleter.delete(fileGroupId) returns success
    mockTempFileDeleter.delete(documentSetId) returns success

    val step = new TestDeleteFileUploadTaskStep(documentSetId, fileGroupId)

    class TestDeleteFileUploadTaskStep(
      override protected val documentSetId: Long,
      override protected val fileGroupId: Long) extends DeleteFileUploadTaskStep {
      override protected def nextStep: TaskStep = FinalStep
      
      override protected val jobDeleter = mockJobDeleter
      override protected val documentSetDeleter = mockDocumentSetDeleter
      override protected val fileGroupDeleter = mockFileGroupDeleter
      override protected val tempFileDeleter = mockTempFileDeleter

    }
  }

}