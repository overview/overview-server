package org.overviewproject.background.filecleanup

import scala.concurrent.duration.DurationInt
import akka.actor.ActorRef
import akka.actor.Props
import akka.testkit.TestProbe
import org.overviewproject.background.filecleanup.DeletedFileCleanerProtocol._
import org.overviewproject.background.filecleanup.FileRemovalRequestQueueProtocol.RemoveFiles
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions


class FileRemovalRequestQueueSpec extends Specification with NoTimeConversions {

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

       fileRemover.expectNoMsg(10 millis)
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