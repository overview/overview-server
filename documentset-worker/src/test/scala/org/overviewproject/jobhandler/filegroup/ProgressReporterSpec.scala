package org.overviewproject.jobhandler.filegroup

import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.filegroup.ProgressReporterProtocol._
import org.overviewproject.test.ActorSystemContext
import org.overviewproject.test.ParameterStore
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.specs2.mutable.Before
import org.specs2.mutable.Specification

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
        def matchParameters(p: (Long, Double, String)) = {
              p._1 must be equalTo (documentSetId)
              p._2 must beCloseTo(fraction, 0.01)
              p._3 must be equalTo (description)
        }
        
        progressReporter.underlyingActor.updateProgressFn.wasLastCalledWithMatch(matchParameters)
      }

      protected def updateJobStateWasCalled(documentSetId: Long, state: DocumentSetCreationJobState) = 
        progressReporter.underlyingActor.updateJobStateFn.wasCalledWith((documentSetId, state))
      
      override def before = {
        progressReporter = TestActorRef(new TestProgressReporter)
      }
    }
  }

}

class TestProgressReporter extends ProgressReporter {
  
  val updateProgressFn = ParameterStore[(Long, Double, String)]
  val updateJobStateFn = ParameterStore[(Long, DocumentSetCreationJobState)]
  
  override protected val storage = new MockStorage

  class MockStorage extends Storage {
    def updateProgress(documentSetId: Long, fraction: Double, description: String): Unit =
      updateProgressFn.store((documentSetId, fraction, description))
    
    def updateJobState(documentSetId: Long, state: DocumentSetCreationJobState): Unit = 
      updateJobStateFn.store((documentSetId, state))
  }
}