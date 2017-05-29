package com.overviewdocs.background.filecleanup

import akka.actor.{ActorRef,Props}
import akka.testkit.{TestActor,TestProbe}
import org.specs2.mock.Mockito
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration
import scala.concurrent.Future

import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.background.filecleanup.DeletedFileCleanerProtocol._
import com.overviewdocs.background.filecleanup.FileCleanerProtocol._

class DeletedFileCleanerSpec extends Specification with Mockito {
  sequential

  "DeletedFileCleaner" should {

    "request file removal" in new DeletedFileScope {
      deletedFileCleaner ! RemoveDeletedFiles

      cleaner.expectMsg(Clean(0))
    }

    "request next file removal when previous one is complete" in new DeletedFileScope {
      deletedFileCleaner ! RemoveDeletedFiles
      
      cleaner.expectMsg(Clean(0))
      cleaner.expectNoMsg(Duration.Zero)
      
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
      cleaner.expectNoMsg(Duration.Zero)
    }
    
    "notify requester that file removal is complete when no deleted files are found" in new NoDeletedFileScope {
      deletedFileCleaner ! RemoveDeletedFiles
      expectMsg(FileRemovalComplete)
    }

  }

  trait DeletedFileScope extends ActorSystemContext {
    def fileIds = Vector(0L, 1L)

    lazy val cleaner = TestProbe()
    lazy val deletedFileCleaner = system.actorOf(Props(new TestDeletedFileCleaner(cleaner.ref, fileIds)))
  }
  
  trait NoDeletedFileScope extends DeletedFileScope {
    override def fileIds = Vector.empty
  }
  
  trait CompletingCleanerScope extends DeletedFileScope {
    override lazy val cleaner = {
      val ret = TestProbe()
      ret.setAutoPilot(new TestActor.AutoPilot {
      def run(sender: ActorRef, msg: Any): TestActor.AutoPilot = msg match {
        case Clean(id) => { 
          sender.tell(CleanComplete(id), testActor)
          TestActor.KeepRunning 
        }
      }})
      ret
    }
  }

  
  class TestDeletedFileCleaner(val fileCleaner: ActorRef, fileIds: Seq[Long]) extends DeletedFileCleaner {
    override protected val deletedFileFinder = smartMock[DeletedFileFinder]
    deletedFileFinder.deletedFileIds returns Future.successful(fileIds)
  }
}
