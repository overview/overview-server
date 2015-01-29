package org.overviewproject.background.filegroupcleanup

import akka.actor.{ ActorRef, Props }
import akka.testkit.TestProbe
import scala.concurrent.Future
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before


class DeletedFileGroupCleanerSpec extends Specification with Mockito {
  
  "DeletedFileGroupCleaner" should {
    
    "send FileGroup removal requests to queue on start up" in new DeletedFileGroupScope {
      val requests = queue.receiveN(numberOfFileGroups)
      
      requests must containTheSameElementsAs(deletedFileGroupIds.map(RemoveFileGroup))
    }
    
    "die after requests have been sent" in new DeletedFileGroupScope {
      supervisor.expectTerminated(cleaner)
    }
  }

  abstract class DeletedFileGroupScope extends ActorSystemContext with Before {
    val numberOfFileGroups = 10
    val deletedFileGroupIds = Seq.range[Long](0, numberOfFileGroups)
    var supervisor: TestProbe = _ 
    var queue: TestProbe = _
    var cleaner: ActorRef = _

    override def before = {
      supervisor = TestProbe()
      queue = TestProbe()
      cleaner = system.actorOf(Props(new TestDeletedFileGroupCleaner(queue.ref, deletedFileGroupIds)))
      
      supervisor watch cleaner
    }
  }
  
  class TestDeletedFileGroupCleaner(queue: ActorRef, ids: Seq[Long]) extends DeletedFileGroupCleaner {
    import context._
    override protected val deletedFileGroupFinder = smartMock[DeletedFileGroupFinder]
    deletedFileGroupFinder.deletedFileGroupIds returns Future { ids }
    
    override protected val fileGroupRemovalRequestQueue = queue
  }
}