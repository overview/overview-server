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

class DeleteFileUploadTaskStepSpec extends Specification with Mockito with NoTimeConversions {

  "DeleteFileUploadTaskStep" should {

    "complete when both deleters complete" in new FileUploadScope {
      val result = Future { step.execute }

      result.isCompleted must beFalse

      documentSetDeleted.success(())

      Await.result(result, 10 millis) must throwA[TimeoutException]

      fileGroupDeleted.success(())
      Await.result(result, 10 millis)

      result.isCompleted must beTrue

      result must beEqualTo(DeleteFileUploadComplete(documentSetId, fileGroupId)).await
    }
  }

  trait FileUploadScope extends Scope {
    val documentSetId = 1l
    val fileGroupId = 13l
    val documentSetDeleted = Promise[Unit]()
    val fileGroupDeleted = Promise[Unit]()

    val documentSetDeleter = smartMock[DocumentSetDeleter]
    val fileGroupDeleter = smartMock[FileGroupDeleter]
    documentSetDeleter.delete(documentSetId) returns documentSetDeleted.future
    fileGroupDeleter.delete(fileGroupId) returns fileGroupDeleted.future

    val step = new DeleteFileUploadTaskStep(documentSetId, fileGroupId, documentSetDeleter, fileGroupDeleter)
  }

}