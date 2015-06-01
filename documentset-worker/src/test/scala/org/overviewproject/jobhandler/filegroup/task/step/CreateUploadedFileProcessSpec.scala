package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future
import slick.jdbc.JdbcBackend.Session
import akka.actor.ActorRef

import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession
import org.overviewproject.jobhandler.filegroup.task.UploadedFileProcessCreator
import org.overviewproject.jobhandler.filegroup.task.UploadProcessOptions
import org.overviewproject.jobhandler.filegroup.task.process.UploadedFileProcess
import org.overviewproject.models.GroupedFileUpload
import org.overviewproject.util.BulkDocumentWriter

import org.specs2.mock.Mockito

class CreateUploadedFileProcessSpec extends SlickSpecification with Mockito {

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
      override protected val uploadedFile: GroupedFileUpload)(implicit val session: Session)
      extends CreateUploadedFileProcess with SlickClientInSession {

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