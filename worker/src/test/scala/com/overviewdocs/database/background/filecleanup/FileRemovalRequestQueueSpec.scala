package com.overviewdocs.background.filecleanup

import akka.actor.ActorRef
import akka.actor.Props
import akka.testkit.TestProbe
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration

import com.overviewdocs.background.filecleanup.DeletedFileCleanerProtocol._
import com.overviewdocs.background.filecleanup.FileRemovalRequestQueueProtocol.RemoveFiles
import com.overviewdocs.test.ActorSystemContext

class FileRemovalRequestQueueSpec extends Specification {
  sequential

  trait QueueScope extends ActorSystemContext {
    class TestFileRemovalRequestQueue(val fileRemover: ActorRef) extends FileRemovalRequestQueue

    lazy val fileRemover: TestProbe = TestProbe()
    lazy val fileRemovalQueue: ActorRef = system.actorOf(Props(new TestFileRemovalRequestQueue(fileRemover.ref)))
  }

  "FileRemovalRequestQueue" should {

    "send a request on startup" in new QueueScope {
      fileRemovalQueue
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
      fileRemovalQueue

      fileRemover.expectMsg(RemoveDeletedFiles)
      fileRemover.reply(FileRemovalComplete)

      fileRemovalQueue ! RemoveFiles
      fileRemover.expectMsg(RemoveDeletedFiles)
    }
  }
}
