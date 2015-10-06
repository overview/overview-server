package com.overviewdocs.jobhandler.filegroup

import akka.actor.ActorSelection
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import com.overviewdocs.jobhandler.filegroup.ClusteringJobQueueProtocol.ClusterDocumentSet
import com.overviewdocs.test.ActorSystemContext
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import com.overviewdocs.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._

class ClusteringJobQueueSpec extends Specification with Mockito {
  sequential

  "ClusteringJobQueue" should {

    "transition to a clustering job" in new ClusteringJobQueueContext {

      clusteringJobQueue ! ClusterDocumentSet(documentSetId)

      there was one(clusteringJobQueue.underlyingActor.storage).transitionToClusteringJob(documentSetId)
    }

    "notify fileGroupRemovalRequestQueue" in new ClusteringJobQueueContext {
      clusteringJobQueue ! ClusterDocumentSet(documentSetId)

      queue.expectMsg(RemoveFileGroup(fileGroupId))
    }

    trait ClusteringJobQueueContext extends ActorSystemContext with Before {

      protected val documentSetId: Long = 1l
      protected val fileGroupId: Long = 10l
      
      protected var queue: TestProbe = _
      protected var clusteringJobQueue: TestActorRef[TestClusteringJobQueue] = _

      def before = {
        queue = TestProbe()
        val queueSelection = system.actorSelection(queue.ref.path.toString)
        clusteringJobQueue = TestActorRef(new TestClusteringJobQueue(queueSelection, fileGroupId))
      }

    }

  }

  class TestClusteringJobQueue(queue: ActorSelection, fileGroupId: Long) extends ClusteringJobQueue {

    override val fileGroupRemovalRequestQueue = queue
    override val storage = smartMock[Storage]
    
    storage.transitionToClusteringJob(any) returns Some(fileGroupId)
  }
}


