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
import akka.testkit.TestProbe
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol.StartClustering

class ClusteringJobQueueSpec extends Specification {

  "ClusteringJobQueue" should {

    "set job state to NOT_STARTED when receiving a job" in new ClusteringJobQueueContext {

      clusteringJobQueue ! ClusterDocumentSet(documentSetId)

      progressReporter.expectMsg(StartClustering(documentSetId))
    }

    trait ClusteringJobQueueContext extends ActorSystemContext with Before {

      protected val documentSetId: Long = 1l
      
      protected var progressReporter: TestProbe = _
      protected var clusteringJobQueue: TestActorRef[ClusteringJobQueue] = _

      def before = {
        progressReporter = TestProbe()
        clusteringJobQueue = TestActorRef(ClusteringJobQueue(progressReporter.ref))
      }

    }

  }
}