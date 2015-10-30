package controllers

import org.specs2.specification.Scope
import scala.concurrent.Future

import com.overviewdocs.messages.DocumentSetCommands
import controllers.util.JobQueueSender

class ReclusterJobControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockStorage = smartMock[ReclusterJobController.Storage]
    val mockJobQueue = smartMock[JobQueueSender]
    val controller = new ReclusterJobController with TestController {
      override val storage = mockStorage
      override val jobQueue = mockJobQueue
    }
  }

  "ReclusterJobController" should {
    "delete" should {
      trait DeleteScope extends BaseScope {
        def request = fakeAuthorizedRequest
        def delete(jobId: Long) = controller.delete(jobId)(request)
      }

      "mark job deleted" in new DeleteScope {
        mockStorage.markJobCancelledAndGetDocumentSetId(1L) returns Future.successful(Some(2L))
        h.status(delete(1L))
        there was one(mockStorage).markJobCancelledAndGetDocumentSetId(1L)
      }

      "send a cancel command if the job exists" in new DeleteScope {
        mockStorage.markJobCancelledAndGetDocumentSetId(1L) returns Future.successful(Some(2L))
        h.status(delete(1L)) must beEqualTo(h.NO_CONTENT)
        there was one(mockJobQueue).send(DocumentSetCommands.CancelJob(2L, 1L))
      }

      "not send a cancel command if the job does not exist" in new DeleteScope {
        mockStorage.markJobCancelledAndGetDocumentSetId(1L) returns Future.successful(None)
        h.status(delete(1L)) must beEqualTo(h.NO_CONTENT)
        there was no(mockJobQueue).send(any[DocumentSetCommands.Command])
      }
    }
  }
}
