package org.overviewproject.background.filecleanup

import akka.actor.{ ActorRef, Props }
import akka.testkit.TestActor
import akka.testkit.TestProbe
import scala.concurrent.duration._
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.test.ActorSystemContext
import org.overviewproject.background.filecleanup.DeletedFileCleanerProtocol._
import org.overviewproject.background.filecleanup.FileCleanerProtocol._
import scala.concurrent.Future
import org.specs2.time.NoTimeConversions


class DeletedFileCleanerSpec extends Specification with Mockito with NoTimeConversions {

  "DeletedFileCleaner" should {

    "request file removal" in new DeletedFileScope {
      deletedFileCleaner ! RemoveDeletedFiles

      cleaner.expectMsg(Clean(0))
    }

    "request next file removal when previous one is complete" in new DeletedFileScope {
      deletedFileCleaner ! RemoveDeletedFiles
      
      cleaner.expectMsg(Clean(0))
      cleaner.expectNoMsg(10 millis)
      
      deletedFileCleaner ! CleanComplete(0)
      cleaner.expectMsg(Clean(1))
    }
    
    "notify requester when file removal is complete" in new CompletingCleanerScope {
      deletedFileCleaner ! RemoveDeletedFiles
      expectMsg(FileRemovalComplete)
    }
    

    "handle request recived after previous request completed" in new CompletingCleanerScope {
      deletedFileCleaner ! RemoveDeletedFiles
      expectMsg(FileRemovalComplete)
      
      deletedFileCleaner ! RemoveDeletedFiles
      expectMsg(FileRemovalComplete)
    }
    
    "ignore request received while previous request is in progress" in new DeletedFileScope {
      deletedFileCleaner ! RemoveDeletedFiles
      
      cleaner.expectMsg(Clean(0))
      cleaner.reply(CleanComplete(0))
      
      deletedFileCleaner ! RemoveDeletedFiles 
      
      cleaner.expectMsg(Clean(1))
      cleaner.reply(CleanComplete(1))
      
      expectMsg(FileRemovalComplete)
      cleaner.expectNoMsg(10 millis)
    }
    
    "notify requester that file removal is complete when no deleted files are found" in new NoDeletedFileScope {
      deletedFileCleaner ! RemoveDeletedFiles
      expectMsg(FileRemovalComplete)
    }

  }

  abstract class DeletedFileScope extends ActorSystemContext with Before {
    def fileIds = Vector(0L, 1L)
    var deletedFileCleaner: ActorRef = _
    var cleaner: TestProbe = _
    
    override def before = {
      cleaner = TestProbe()
      deletedFileCleaner = system.actorOf(Props(new TestDeletedFileCleaner(cleaner.ref, fileIds)))
    }
  }
  
  abstract class NoDeletedFileScope extends DeletedFileScope {
    override def fileIds = Vector.empty
  }
  
  abstract class CompletingCleanerScope extends DeletedFileScope {
    
    override def before = {
      super.before
      cleaner.setAutoPilot(new CompletingCleaner)
    }
    
    class CompletingCleaner extends TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): TestActor.AutoPilot =
        msg match {
        case Clean(id) => { 
          sender.tell(CleanComplete(id), testActor)
          TestActor.KeepRunning 
        }
      }
    }
  }

  
  class TestDeletedFileCleaner(val fileCleaner: ActorRef, fileIds: Seq[Long]) extends DeletedFileCleaner {
    override protected val deletedFileFinder = smartMock[DeletedFileFinder]
    deletedFileFinder.deletedFileIds returns Future.successful(fileIds)
  }
}