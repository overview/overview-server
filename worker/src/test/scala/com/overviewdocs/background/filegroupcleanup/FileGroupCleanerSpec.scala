package com.overviewdocs.background.filegroupcleanup

import org.specs2.mutable.Specification
import com.overviewdocs.test.ActorSystemContext
import org.specs2.mock.Mockito
import scala.concurrent.{Future,Promise}
import scala.concurrent.duration.Duration
import akka.actor.Props
import akka.testkit.TestActorRef

import FileGroupCleanerProtocol.{Clean,CleanComplete}

class FileGroupCleanerSpec extends Specification with Mockito {
  sequential

  trait FileGroupCleanerScope extends ActorSystemContext {
    val mockFileGroupRemover = smartMock[FileGroupRemover]

    class TestFileGroupCleaner extends FileGroupCleaner {
      override protected val fileGroupRemover = mockFileGroupRemover
    }

    lazy val fileGroupCleaner = TestActorRef(Props(new TestFileGroupCleaner))
  }

  "FileGroupCleaner" should {
    "start cleaning" in new FileGroupCleanerScope {
      mockFileGroupRemover.remove(any) returns Future.unit
      fileGroupCleaner ! Clean(1L)
      there was one(mockFileGroupRemover).remove(1L)
    }

    "notify requester when done" in new FileGroupCleanerScope {
      val promise = Promise[Unit]()
      mockFileGroupRemover.remove(any) returns promise.future

      fileGroupCleaner ! Clean(1L)
      expectNoMsg(Duration.Zero)

      promise.success(())
      expectMsg(CleanComplete(1L))
    }

    "notify requester on failure" in new FileGroupCleanerScope {
      mockFileGroupRemover.remove(any) returns Future.failed(new Exception("fail"))
      fileGroupCleaner ! Clean(1L)
      expectMsg(CleanComplete(1L))
    }
  }
}
