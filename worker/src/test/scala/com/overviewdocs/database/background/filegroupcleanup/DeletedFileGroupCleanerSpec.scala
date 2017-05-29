package com.overviewdocs.background.filegroupcleanup

import akka.actor.{ ActorRef, Props }
import akka.testkit.TestProbe
import scala.concurrent.Future
import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import com.overviewdocs.background.filegroupcleanup.FileGroupRemovalRequestQueueProtocol._
import com.overviewdocs.test.ActorSystemContext


class DeletedFileGroupCleanerSpec extends Specification with Mockito {
  sequential

  trait DeletedFileGroupScope extends ActorSystemContext {
    val deletedFileGroupIds = Seq(1L, 2L, 3L)

    lazy val supervisor = TestProbe()
    lazy val queue = TestProbe()
    lazy val cleaner: ActorRef = {
      val ret = system.actorOf(Props(new TestDeletedFileGroupCleaner(queue.ref, deletedFileGroupIds)))
      supervisor.watch(ret)
      ret
    }

    class TestDeletedFileGroupCleaner(queue: ActorRef, ids: Seq[Long]) extends DeletedFileGroupCleaner {
      override protected val deletedFileGroupFinder = smartMock[DeletedFileGroupFinder]
      deletedFileGroupFinder.indexIds returns Future.successful(ids)

      override protected val fileGroupRemovalRequestQueue = queue
    }
  }

  "DeletedFileGroupCleaner" should {
    "send FileGroup removal requests to queue on start up" in new DeletedFileGroupScope {
      cleaner
      val requests = queue.receiveN(3)
      requests must containTheSameElementsAs(deletedFileGroupIds.map(RemoveFileGroup))
    }

    "die after requests have been sent" in new DeletedFileGroupScope {
      cleaner
      supervisor.expectTerminated(cleaner)
    }
  }
}
