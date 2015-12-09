package com.overviewdocs.background.filecleanup

import akka.actor.ActorRef
import akka.actor.Props
import akka.testkit.TestProbe
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration

import com.overviewdocs.background.filecleanup.DeletedFileCleanerProtocol._
import com.overviewdocs.background.filecleanup.FileRemovalRequestQueueProtocol.RemoveFiles
import com.overviewdocs.test.ActorSystemContext

class FileRemovalRequestQueueSpec extends Specification {
  sequential

  "FileRemovalRequestQueue" should {

    "send a request on startup" in new QueueScope {
      fileRemover.expectMsg(RemoveDeletedFiles)
    }

    "send only one request if new request are received before previous request is complete" in new QueueScope {
       fileRemovalQueue ! RemoveFiles
       fileRemovalQueue ! RemoveFiles
       
       
       fileRemover.expectMsg(RemoveDeletedFiles)
       fileRemover.reply(FileRemovalComplete)
       fileRemover.expectMsg(RemoveDeletedFiles)
       fileRemover.reply(FileRemovalComplete)

       fileRemover.expectNoMsg(Duration.Zero)
    }
    
    "send a request received after a previous one completes" in new QueueScope {
      fileRemover.expectMsg(RemoveDeletedFiles)
      fileRemover.reply(FileRemovalComplete)
      
      fileRemovalQueue ! RemoveFiles
      fileRemover.expectMsg(RemoveDeletedFiles)
    } 
    
  }

  abstract class QueueScope extends ActorSystemContext with Before {
    var fileRemover: TestProbe = _
    var fileRemovalQueue: ActorRef = _

    override def before = {
      fileRemover = TestProbe()
      fileRemovalQueue = system.actorOf(Props(new TestFileRemovalRequestQueue(fileRemover.ref)))
    }
  }
  class TestFileRemovalRequestQueue(val fileRemover: ActorRef) extends FileRemovalRequestQueue
}
