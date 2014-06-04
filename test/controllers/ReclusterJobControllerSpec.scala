package controllers

import org.specs2.specification.Scope

import org.overviewproject.jobs.models.DeleteTreeJob

class ReclusterJobControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockStorage = mock[ReclusterJobController.Storage]
    val mockJobQueue = mock[ReclusterJobController.JobQueue]
    val controller = new ReclusterJobController {
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
        there was one(mockJobQueue).send(DeleteTreeJob(jobId))
      }

      "mark job deleted if reclustering job has started clustering" in new DeleteScope {
        mockStorage.cancelJob(jobId) returns ReclusterJobController.JobWasRunning
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockStorage).cancelJob(jobId)
        there was no(mockJobQueue).send(DeleteTreeJob(jobId))
      }

      "do nothing if the reclustering job disappeared" in new DeleteScope {
        mockStorage.cancelJob(jobId) returns ReclusterJobController.JobNotFound
        h.status(result) must beEqualTo(h.NO_CONTENT)
        there was one(mockStorage).cancelJob(jobId)
        there was no(mockJobQueue).send(DeleteTreeJob(jobId))
      }
    }
  }
}
