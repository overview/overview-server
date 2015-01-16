package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import scala.concurrent.Promise
import org.overviewproject.database.DocumentSetDeleter
import org.overviewproject.database.FileGroupDeleter
import org.specs2.specification.Scope
import org.specs2.time.NoDurationConversions
import org.specs2.time.NoTimeConversions
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.concurrent.TimeoutException
import org.overviewproject.database.DocumentSetCreationJobDeleter

class DeleteFileUploadTaskStepSpec extends Specification with Mockito with NoTimeConversions {

  "DeleteFileUploadTaskStep" should {

    "complete when all deleters complete" in new FileUploadScope {
      val result = Future { step.execute }

      result.isCompleted must beFalse

      jobDeleted.success(())
      documentSetDeleted.success(())

      Await.result(result, 10 millis) must throwA[TimeoutException]

      fileGroupDeleted.success(())
      Await.result(result, 10 millis)

      result.isCompleted must beTrue

      result must beEqualTo(DeleteFileUploadComplete(documentSetId, fileGroupId)).await
    }
    
    "delete job before starting other deleters" in new FileUploadScope {
      val result = Future { step.execute }
      
      documentSetDeleted.success(())
      fileGroupDeleted.success(())
      
      there was no(documentSetDeleter).delete(documentSetId)
      there was no(fileGroupDeleter).delete(fileGroupId)
    }
  }

  trait FileUploadScope extends Scope {
    val documentSetId = 1l
    val fileGroupId = 13l
    val jobDeleted = Promise[Unit]()
    val documentSetDeleted = Promise[Unit]()
    val fileGroupDeleted = Promise[Unit]()

    val jobDeleter = smartMock[DocumentSetCreationJobDeleter]
    val documentSetDeleter = smartMock[DocumentSetDeleter]
    val fileGroupDeleter = smartMock[FileGroupDeleter]
    
    jobDeleter.deleteByDocumentSet(documentSetId) returns jobDeleted.future
    documentSetDeleter.delete(documentSetId) returns documentSetDeleted.future
    fileGroupDeleter.delete(fileGroupId) returns fileGroupDeleted.future

    val step = new DeleteFileUploadTaskStep(documentSetId, fileGroupId, jobDeleter, documentSetDeleter, fileGroupDeleter)
  }

}