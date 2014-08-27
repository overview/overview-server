package org.overviewproject.jobhandler.filegroup

import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import org.overviewproject.jobhandler.filegroup.ClusteringJobQueueProtocol.ClusterDocumentSet
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito

class ClusteringJobQueueSpec extends Specification with Mockito {

  "ClusteringJobQueue" should {

    "transition to a clustering job" in new ClusteringJobQueueContext {

      clusteringJobQueue ! ClusterDocumentSet(documentSetId)

      there was one(clusteringJobQueue.underlyingActor.storage).transitionToClusteringJob(documentSetId)
    }

    trait ClusteringJobQueueContext extends ActorSystemContext with Before {

      protected val documentSetId: Long = 1l
      
      protected var clusteringJobQueue: TestActorRef[TestClusteringJobQueue] = _

      def before = {
        clusteringJobQueue = TestActorRef(new TestClusteringJobQueue)
      }

    }

  }
}

class TestClusteringJobQueue extends ClusteringJobQueue with Mockito {
  
  override val storage = mock[Storage]
}