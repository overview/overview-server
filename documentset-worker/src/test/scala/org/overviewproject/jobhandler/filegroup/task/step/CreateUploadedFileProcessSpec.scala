package com.overviewdocs.jobhandler.filegroup.task.step

import akka.actor.ActorRef
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import slick.jdbc.JdbcBackend.Session
import org.specs2.mock.Mockito

import com.overviewdocs.jobhandler.filegroup.task.UploadedFileProcessCreator
import com.overviewdocs.jobhandler.filegroup.task.UploadProcessOptions
import com.overviewdocs.jobhandler.filegroup.task.process.UploadedFileProcess
import com.overviewdocs.models.GroupedFileUpload
import com.overviewdocs.test.DbSpecification
import com.overviewdocs.util.BulkDocumentWriter

class CreateUploadedFileProcessSpec extends DbSpecification with Mockito {

  "CreateUploadedFileProcess" should {

    "create next step based on uploaded file" in new UploadedFileScope {
      createUploadedFileProcess.execute must beEqualTo(selectedStep).await
    }
  }

  trait UploadedFileScope extends DbScope {
    val documentSetId = 1l
    val fileGroup = factory.fileGroup()
    val uploadedFile = factory.groupedFileUpload(fileGroupId = fileGroup.id)

    val processCreator = smartMock[UploadedFileProcessCreator]
    val mockOptions = smartMock[UploadProcessOptions]
    val idSupplier = smartMock[ActorRef]
    val mockBulkDocumentWriter = smartMock[BulkDocumentWriter]

    val selectedProcess = smartMock[UploadedFileProcess]
    val selectedStep = smartMock[TaskStep]

    selectedProcess.start(uploadedFile) returns Future.successful(selectedStep)

    val createUploadedFileProcess = new TestCreateUploadedFileProcess(documentSetId, uploadedFile)

    class TestCreateUploadedFileProcess(
      override protected val documentSetId: Long,
      override protected val uploadedFile: GroupedFileUpload
    ) extends CreateUploadedFileProcess {
      override protected val executor: ExecutionContext = implicitly
      override protected val documentIdSupplier = idSupplier
      override protected val options = mockOptions
      override protected val uploadedFileProcessCreator = processCreator
      override protected val bulkDocumentWriter = mockBulkDocumentWriter

      uploadedFileProcessCreator.create(
        be_===(uploadedFile),
        be_===(options),
        be_===(documentSetId),
        any,
        be_===(bulkDocumentWriter)) returns selectedProcess

    }
  }

}
