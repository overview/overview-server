package controllers

import org.specs2.specification.Scope

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
        val jobId = 10L
        def request = fakeAuthorizedRequest
        def result = controller.delete(jobId)(request)
      }

      "mark job deleted and send delete job request if reclustering job has not started clustering" in new DeleteScope {
        mockStorage.cancelJob(jobId) returns ReclusterJobController.JobWasNotRunning
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockStorage).cancelJob(jobId)
        there was one(mockJobQueue).send(DocumentSetCommands.DeleteDocumentSetJob(-1, jobId))
      }

      "mark job deleted if reclustering job has started clustering" in new DeleteScope {
        mockStorage.cancelJob(jobId) returns ReclusterJobController.JobWasRunning
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockStorage).cancelJob(jobId)
        there was no(mockJobQueue).send(DocumentSetCommands.DeleteDocumentSetJob(-1, jobId))
      }

      "do nothing if the reclustering job disappeared" in new DeleteScope {
        mockStorage.cancelJob(jobId) returns ReclusterJobController.JobNotFound
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockStorage).cancelJob(jobId)
        there was no(mockJobQueue).send(DocumentSetCommands.DeleteDocumentSetJob(-1, jobId))
      }
    }
  }
}
