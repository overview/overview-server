package org.overviewproject.background.filecleanup

import akka.actor.{ ActorRef, Props }
import akka.testkit.TestProbe
import scala.concurrent.duration._
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import org.overviewproject.background.filecleanup.DeletedFileRemoverProtocol._
import org.overviewproject.background.filecleanup.FileCleanerProtocol._
import scala.concurrent.Future
import org.specs2.time.NoTimeConversions
import akka.testkit.TestActor

class DeletedFileRemoverSpec extends Specification with Mockito with NoTimeConversions {

  "DeletedFileRemover" should {

    "request file removal" in new DeletedFileScope {
      remover ! RemoveDeletedFiles

      cleaner.expectMsg(Clean(0))
    }

    "request next file removal when previous one is complete" in new DeletedFileScope {
      remover ! RemoveDeletedFiles
      
      cleaner.expectMsg(Clean(0))
      cleaner.expectNoMsg(10 millis)
      
      remover ! CleanComplete(0)
      cleaner.expectMsg(Clean(1))
    }
    
    "notify requester when file removal is complete" in new CompletingCleanerScope {
      remover ! RemoveDeletedFiles
      expectMsg(FileRemovalComplete)
    }
    

    "handle request recived after previous request completed" in new CompletingCleanerScope {
      remover ! RemoveDeletedFiles
      expectMsg(FileRemovalComplete)
      
      remover ! RemoveDeletedFiles
      expectMsg(FileRemovalComplete)
    }
    
    "ignore request received while previous request is in progress" in new DeletedFileScope {
      remover ! RemoveDeletedFiles
      
      cleaner.expectMsg(Clean(0))
      cleaner.reply(CleanComplete(0))
      
      remover ! RemoveDeletedFiles 
      
      cleaner.expectMsg(Clean(1))
      cleaner.reply(CleanComplete(1))
      
      expectMsg(FileRemovalComplete)
      cleaner.expectNoMsg(10 millis)
    }
    
    "notify requester that file removal is complete when no deleted files are found" in new NoDeletedFileScope {
      remover ! RemoveDeletedFiles
      expectMsg(FileRemovalComplete)
    }

  }

  abstract class DeletedFileScope extends ActorSystemContext with Before {
    def fileIds = Seq(0L, 1L)
    var remover: ActorRef = _
    var cleaner: TestProbe = _
    
    override def before = {
      cleaner = TestProbe()
      remover = system.actorOf(Props(new TestDeletedFileRemover(cleaner.ref, fileIds)))
    }
  }
  
  abstract class NoDeletedFileScope extends DeletedFileScope {
    override def fileIds = Seq.empty
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

  
  class TestDeletedFileRemover(val fileCleaner: ActorRef, fileIds: Seq[Long]) extends DeletedFileRemover {
    override protected val deletedFileScanner = smartMock[DeletedFileScanner]
    deletedFileScanner.deletedFileIds returns Future.successful(fileIds)
  }
}