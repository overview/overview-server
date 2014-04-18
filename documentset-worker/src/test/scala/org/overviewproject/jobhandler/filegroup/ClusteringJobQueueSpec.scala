package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.ClusteringJobQueueProtocol.ClusterDocumentSet
import akka.actor.Props
import akka.agent.Agent
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import akka.testkit.TestActorRef

class TestClusteringJobQueue extends ClusteringJobQueue {
  import ExecutionContext.Implicits.global
  private val storedSubmitJobParameter: Agent[Option[Long]] = Agent(None)

  class MockStorage extends Storage {
    override def submitJob(documentSetId: Long): Unit =
      storedSubmitJobParameter send Some(documentSetId)
  }

  override protected val storage = new MockStorage

  def submitJobCallsInProgress: Future[Option[Long]] = storedSubmitJobParameter.future
  def submitJobParameter: Option[Long] = storedSubmitJobParameter()
}

class ClusteringJobQueueSpec extends Specification {

  "ClusteringJobQueue" should {

    "set job state to NOT_STARTED when receiving a job" in new ClusteringJobQueueContext {

      clusteringJobQueue ! ClusterDocumentSet(documentSetId)

      submitJobWasCalledWith(documentSetId)
    }

    trait ClusteringJobQueueContext extends ActorSystemContext with Before {

      protected val documentSetId: Long = 1l

      protected var clusteringJobQueue: TestActorRef[TestClusteringJobQueue] = _

      def before = {
        clusteringJobQueue = TestActorRef[TestClusteringJobQueue]
      }

      def submitJobWasCalledWith(documentSetId: Long) = {
        val submitJobParameters = clusteringJobQueue.underlyingActor.submitJobCallsInProgress
        awaitCond(submitJobParameters.isCompleted)
        
        clusteringJobQueue.underlyingActor.submitJobParameter must beSome(documentSetId)
      }
    }

  }
}