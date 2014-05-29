package org.overviewproject.jobhandler.filegroup

import scala.collection.immutable.Queue
import scala.concurrent.ExecutionContext
import akka.agent.Agent
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import scala.concurrent.Future
import org.overviewproject.tree.orm.DocumentSetCreationJobState
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

class ProgressReporterSpec extends Specification {

  "ProgressReporter" should {

    "initialize progress when start received" in new ProgressReporterContext {
      progressReporter ! StartJob(documentSetId, numberOfTasks)

      lastProgressStatusMustBe(documentSetId, 0.0, s"processing_files:0:$numberOfTasks")
    }

    "update count on task start" in new ProgressReporterContext {
      progressReporter ! StartJob(documentSetId, numberOfTasks)
      progressReporter ! StartTask(documentSetId, uploadedFileId)

      lastProgressStatusMustBe(documentSetId, 0.0, s"processing_files:1:$numberOfTasks")
    }

    "update fraction complete on task done" in new ProgressReporterContext {
      progressReporter ! StartJob(documentSetId, numberOfTasks)
      progressReporter ! StartTask(documentSetId, uploadedFileId)
      progressReporter ! CompleteTask(documentSetId, uploadedFileId)
      
      lastProgressStatusMustBe(documentSetId, progressFraction * 1.0 / numberOfTasks, s"processing_files:1:$numberOfTasks")
    }

    "ignore updates after job is complete" in new ProgressReporterContext {
      progressReporter ! StartJob(documentSetId, numberOfTasks)
      progressReporter ! StartTask(documentSetId, uploadedFileId)
      progressReporter ! CompleteTask(documentSetId, uploadedFileId)
      
      progressReporter ! CompleteJob(documentSetId)
      progressReporter ! StartTask(documentSetId, uploadedFileId)
      
      lastProgressStatusMustBe(documentSetId, progressFraction * 1.0 / numberOfTasks, s"processing_files:1:$numberOfTasks")
    }
      
    "set job state to start clustering" in new ProgressReporterContext {
      progressReporter  ! StartClustering(documentSetId)
      
      updateJobStateWasCalled(documentSetId, NotStarted)
    }
    
    trait ProgressReporterContext extends ActorSystemContext with Before {
      protected val documentSetId = 1l
      protected val numberOfTasks = 5
      protected val uploadedFileId = 10l
      protected val progressFraction = 0.25
      
      protected var progressReporter: TestActorRef[TestProgressReporter] = _

      protected def lastProgressStatusMustBe(documentSetId: Long, fraction: Double, description: String) = {
        val pendingCalls = progressReporter.underlyingActor.updateProgressCallsInProgress
        awaitCond(pendingCalls.isCompleted)
        progressReporter.underlyingActor.updateProgressParemeters.lastOption must
          beSome.like {
            case p =>
              p._1 must be equalTo (documentSetId)
              p._2 must beCloseTo(fraction, 0.01)
              p._3 must be equalTo (description)
          }
      }

      protected def updateJobStateWasCalled(documentSetId: Long, state: DocumentSetCreationJobState) = {
        val pendingCalls = progressReporter.underlyingActor.updateJobStateCallsInProgress
        awaitCond(pendingCalls.isCompleted)
        progressReporter.underlyingActor.updateJobStateCallParameters.headOption must beSome((documentSetId, state))
      }
      
      override def before = {
        progressReporter = TestActorRef(new TestProgressReporter)
      }
    }
  }

}

class TestProgressReporter extends ProgressReporter {
  import ExecutionContext.Implicits.global

  private val updateProgressParameters: Agent[Queue[(Long, Double, String)]] = Agent(Queue.empty)

  def updateProgressCallsInProgress: Future[Queue[(Long, Double, String)]] = updateProgressParameters.future
  def updateProgressParemeters: Queue[(Long, Double, String)] = updateProgressParameters.get

  private val updateJobStateParameters: Agent[Queue[(Long, DocumentSetCreationJobState)]] = Agent(Queue.empty)
  
  def updateJobStateCallsInProgress: Future[Queue[(Long, DocumentSetCreationJobState)]] = updateJobStateParameters.future
  def updateJobStateCallParameters: Queue[(Long, DocumentSetCreationJobState)] = updateJobStateParameters.get
  
  override protected val storage = new MockStorage

  class MockStorage extends Storage {
    def updateProgress(documentSetId: Long, fraction: Double, description: String): Unit =
      updateProgressParameters send (_.enqueue(documentSetId, fraction, description))
    
    def updateJobState(documentSetId: Long, state: DocumentSetCreationJobState): Unit = 
      updateJobStateParameters send (_.enqueue((documentSetId, state)))
  }
}