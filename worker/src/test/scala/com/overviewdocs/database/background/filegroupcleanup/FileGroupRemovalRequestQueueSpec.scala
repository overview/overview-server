package com.overviewdocs.background.filegroupcleanup

import akka.actor.{ActorRef,Props}
import akka.testkit.TestProbe
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration

import com.overviewdocs.background.filegroupcleanup.FileGroupCleanerProtocol._
import com.overviewdocs.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._
import com.overviewdocs.test.ActorSystemContext

class FileGroupRemovalRequestQueueSpec extends Specification {
  sequential

  trait FileGroupRemovalRequestQueueScope extends ActorSystemContext {
    lazy val cleaner = TestProbe()

    class TestFileGroupRemovalRequestQueue extends FileGroupRemovalRequestQueue {
      override protected val fileGroupCleaner: ActorRef = cleaner.ref
    }

    lazy val requestQueue = system.actorOf(Props(new TestFileGroupRemovalRequestQueue))
  }

  "FileGroupRemovalRequestQueue" should {
    "start file removal when request is received" in new FileGroupRemovalRequestQueueScope {
      requestQueue ! RemoveFileGroup(1L)
      cleaner.expectMsg(Clean(1L))
    }

    "send next request in queue when previous requests completes" in new FileGroupRemovalRequestQueueScope {
      requestQueue ! RemoveFileGroup(1L)
      requestQueue ! RemoveFileGroup(2L)
      cleaner.expectMsg(Clean(1L))
      cleaner.expectNoMsg(Duration.Zero)

      requestQueue.tell(CleanComplete(1L), cleaner.ref)
      cleaner.expectMsg(Clean(2L))
    }
  }
}
