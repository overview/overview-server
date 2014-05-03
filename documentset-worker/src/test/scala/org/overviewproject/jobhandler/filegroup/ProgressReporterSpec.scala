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


class ProgressReporterSpec extends Specification {

  "ProgressReporter" should {

    "initialize progress when start received" in new ProgressReporterContext {
      progressReporter ! StartJob(documentSetId, numberOfTasks)

      progressStatusMustBe(documentSetId, 0.0, s"processing_files:0:$numberOfTasks")
    }

    "update count on task start" in {
      todo
    }

    "update fraction complete on task done" in {
      todo
    }

    trait ProgressReporterContext extends ActorSystemContext with Before {
      protected val documentSetId = 1l
      protected val numberOfTasks = 5

      protected var progressReporter: TestActorRef[TestProgressReporter] = _

      protected def progressStatusMustBe(documentSetId: Long, fraction: Double, description: String) = {
        val pendingCalls = progressReporter.underlyingActor.updateProgressCallsInProgress
        awaitCond(pendingCalls.isCompleted)
        progressReporter.underlyingActor.updateProgressParemeters.headOption must 
          beSome((documentSetId, fraction, description))
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
  
  override protected val storage = new MockStorage 
  
  class MockStorage extends Storage {
   def updateProgress(documentSetId: Long, fraction: Double, description: String): Unit = 
     updateProgressParameters send (_.enqueue(documentSetId, fraction, description))
 }
}