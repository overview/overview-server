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
import org.overviewproject.database.TempFileDeleter

class DeleteFileUploadTaskStepSpec extends Specification with Mockito with NoTimeConversions {

  "DeleteFileUploadTaskStep" should {

    "complete when all deleters complete" in new FileUploadScope {
      val result = Future { step.execute }

      result.isCompleted must beFalse

      jobDeleted.success(())
      documentSetDeleted.success(())

      Await.result(result, 10 millis) must throwA[TimeoutException]

      fileGroupDeleted.success(())
      Await.result(result, 10 millis) must throwA[TimeoutException]
      
      tempFileDeleted.success(())
      
      Await.result(result, 10 millis)

      result.isCompleted must beTrue

      result must beEqualTo(DeleteFileUploadComplete(documentSetId, fileGroupId)).await
    }
    
    "delete job before starting other deleters" in new FileUploadScope {
      val result = Future { step.execute }
      
      tempFileDeleted.success(())
      documentSetDeleted.success(())
      fileGroupDeleted.success(())
      
      there was no(tempFileDeleter).delete(documentSetId)
      there was no(documentSetDeleter).delete(documentSetId)
      there was no(fileGroupDeleter).delete(fileGroupId)
    }
    
    "delete temp files before starting other deleters" in new FileUploadScope {
      val result = Future { step.execute }
      
      jobDeleted.success(())
      documentSetDeleted.success(())
      fileGroupDeleted.success(())
      
      Await.result(result, 10 millis) must throwA[TimeoutException]
      
      there was one(tempFileDeleter).delete(documentSetId)
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
    val tempFileDeleted = Promise[Unit]()
    
    val jobDeleter = smartMock[DocumentSetCreationJobDeleter]
    val documentSetDeleter = smartMock[DocumentSetDeleter]
    val fileGroupDeleter = smartMock[FileGroupDeleter]
    val tempFileDeleter = smartMock[TempFileDeleter]
    
    jobDeleter.deleteByDocumentSet(documentSetId) returns jobDeleted.future
    documentSetDeleter.delete(documentSetId) returns documentSetDeleted.future
    fileGroupDeleter.delete(fileGroupId) returns fileGroupDeleted.future
    tempFileDeleter.delete(documentSetId) returns tempFileDeleted.future

    val step = new DeleteFileUploadTaskStep(documentSetId, fileGroupId, 
        jobDeleter, documentSetDeleter, fileGroupDeleter, tempFileDeleter)
  }

}