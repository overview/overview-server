package com.overviewdocs.background.filegroupcleanup

import akka.actor.{ActorRef,Props}
import akka.testkit.TestProbe
import org.specs2.mutable.{Before,Specification}
import scala.concurrent.duration.Duration

import com.overviewdocs.background.filegroupcleanup.FileGroupCleanerProtocol._
import com.overviewdocs.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._
import com.overviewdocs.test.ActorSystemContext

class FileGroupRemovalRequestQueueSpec extends Specification {
  sequential

  
  "FileGroupRemovalRequestQueue" should {
    
    "start file removal when request is received" in new FileGroupRemovalRequestQueueScope {
      requestQueue ! RemoveFileGroup(fileGroupId)
      
      fileGroupCleaner.expectMsg(Clean(fileGroupId))
    }
    
    "send next request in queue when previous requests completes" in new FileGroupRemovalRequestQueueScope {
      requestQueue ! RemoveFileGroup(fileGroupId)
      requestQueue ! RemoveFileGroup(nextFileGroupId)
      
      fileGroupCleaner.expectMsg(Clean(fileGroupId))
      fileGroupCleaner.expectNoMsg(Duration.Zero)
      
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
