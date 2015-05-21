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


class DeleteFileUploadTaskStepSpec extends Specification with Mockito with NoTimeConversions {

  "DeleteFileUploadTaskStep" should {

    "complete when all deleters complete" in new FileUploadScope {
      val result = step.execute

      result.isCompleted must beFalse

      jobDeleted.success(())
      tempFileDeleted.success(())
      documentSetDeleted.success(())
      fileGroupDeleted.success(())

      result must beEqualTo(FinalStep).await
    }

    "delete job before starting other deleters" in new FileUploadScope {
      val result = step.execute

      tempFileDeleted.success(())
      documentSetDeleted.success(())
      fileGroupDeleted.success(())

      there was one(mockJobDeleter).deleteByDocumentSet(documentSetId)
      there was no(mockTempFileDeleter).delete(documentSetId)
      there was no(mockDocumentSetDeleter).delete(documentSetId)
      there was no(mockFileGroupDeleter).delete(fileGroupId)
    }

    "delete temp files before starting other deleters" in new FileUploadScope {
      val result = step.execute

      jobDeleted.success(())
      documentSetDeleted.success(())
      fileGroupDeleted.success(())

      there was one(mockTempFileDeleter).delete(documentSetId)
      there was no(mockDocumentSetDeleter).delete(documentSetId)
      there was no(mockFileGroupDeleter).delete(fileGroupId)
    }
  }

  trait FileUploadScope extends Scope {
    val documentSetId = 1l
    val fileGroupId = 13l
    val jobDeleted = Promise[Unit]()
    val documentSetDeleted = Promise[Unit]()
    val fileGroupDeleted = Promise[Unit]()
    val tempFileDeleted = Promise[Unit]()

    val mockJobDeleter = smartMock[DocumentSetCreationJobDeleter]
    val mockDocumentSetDeleter = smartMock[DocumentSetDeleter]
    val mockFileGroupDeleter = smartMock[FileGroupDeleter]
    val mockTempFileDeleter = smartMock[TempFileDeleter]

    mockJobDeleter.deleteByDocumentSet(documentSetId) returns jobDeleted.future
    mockDocumentSetDeleter.delete(documentSetId) returns documentSetDeleted.future
    mockFileGroupDeleter.delete(fileGroupId) returns fileGroupDeleted.future
    mockTempFileDeleter.delete(documentSetId) returns tempFileDeleted.future

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