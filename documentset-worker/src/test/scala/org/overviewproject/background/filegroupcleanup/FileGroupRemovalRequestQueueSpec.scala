package org.overviewproject.background.filegroupcleanup

import scala.concurrent.duration._
import akka.actor.{ ActorRef, Props }
import akka.testkit.TestProbe

import org.specs2.mutable.Specification
import org.specs2.mutable.Before
import org.specs2.time.NoTimeConversions
import org.overviewproject.test.ActorSystemContext

import org.overviewproject.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._
import org.overviewproject.background.filegroupcleanup.FileGroupCleanerProtocol._

class FileGroupRemovalRequestQueueSpec extends Specification with NoTimeConversions {

  
  "FileGroupRemovalRequestQueue" should {
    
    "start file removal when request is received" in new FileGroupRemovalRequestQueueScope {
      requestQueue ! RemoveFileGroup(fileGroupId)
      
      fileGroupCleaner.expectMsg(Clean(fileGroupId))
    }
    
    "send next request in queue when previous requests completes" in new FileGroupRemovalRequestQueueScope {
      requestQueue ! RemoveFileGroup(fileGroupId)
      requestQueue ! RemoveFileGroup(nextFileGroupId)
      
      fileGroupCleaner.expectMsg(Clean(fileGroupId))
      fileGroupCleaner.expectNoMsg(10 millis)
      
      requestQueue.tell(CleanComplete(fileGroupId), fileGroupCleaner.ref)
      
      fileGroupCleaner.expectMsg(Clean(nextFileGroupId))
    }
  }
  
  abstract class FileGroupRemovalRequestQueueScope extends ActorSystemContext with Before {
    val fileGroupId = 1l
    val nextFileGroupId = 2l
    
    var requestQueue: ActorRef = _
    var fileGroupCleaner: TestProbe = _
    
    override def before = {
      fileGroupCleaner = TestProbe()
      requestQueue = system.actorOf(Props(new TestFileGroupRemovalRequestQueue(fileGroupCleaner.ref)))
    }
  }
  
  
  class TestFileGroupRemovalRequestQueue(cleaner: ActorRef) extends FileGroupRemovalRequestQueue {
    override protected val fileGroupCleaner = cleaner
  }
}