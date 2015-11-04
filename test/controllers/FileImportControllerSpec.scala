package controllers

import org.specs2.specification.Scope
import scala.concurrent.Future

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.test.factories.{PodoFactory=>factory}
import controllers.backend.{DocumentSetBackend,FileGroupBackend,ImportJobBackend}
import controllers.util.JobQueueSender

class FileImportControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockDocumentSetBackend = mock[DocumentSetBackend]
    val mockFileGroupBackend = mock[FileGroupBackend]
    val mockImportJobBackend = mock[ImportJobBackend]
    val mockJobQueueSender = mock[JobQueueSender]

    val controller = new FileImportController with TestController {
      override val documentSetBackend = mockDocumentSetBackend
      override val fileGroupBackend = mockFileGroupBackend
      override val importJobBackend = mockImportJobBackend
      override val jobQueueSender = mockJobQueueSender
    }
  }

  "delete" should {
    trait DeleteScope extends BaseScope {
      val fileGroupId = 1L
      lazy val result = controller.delete(fileGroupId)(fakeAuthorizedRequest)
    }

    "return NoContent when the FileGroup is already deleted" in new DeleteScope {
      // This tests a race. The auth check means the FileGroup is probably gone.
      mockFileGroupBackend.find(fileGroupId) returns Future.successful(None)
      h.status(result) must beEqualTo(h.NO_CONTENT)
      there was no(mockFileGroupBackend).destroy(any)
      there was no(mockJobQueueSender).send(any)
    }

    "deletes the FileGroup and notifies the worker" in new DeleteScope {
      mockFileGroupBackend.find(fileGroupId) returns Future.successful(Some(factory.fileGroup(addToDocumentSetId=Some(2L))))
      mockFileGroupBackend.destroy(fileGroupId) returns Future.successful(())
      h.status(result) must beEqualTo(h.NO_CONTENT)
      there was one(mockFileGroupBackend).destroy(fileGroupId)
      there was one(mockJobQueueSender).send(DocumentSetCommands.CancelAddDocumentsFromFileGroup(2L, 1L))
    }
  }
}
