package com.overviewdocs.background.filecleanup

import akka.actor._
import akka.testkit.TestActorRef
import FileCleanerProtocol._
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration
import scala.concurrent.{Future,Promise}

import com.overviewdocs.test.ActorSystemContext

class FileCleanerSpec extends Specification with Mockito {
  sequential

  "FileCleaner" should {
    trait FileCleaningScope extends ActorSystemContext {
      val mockFileRemover = smartMock[FileRemover]

      class TestFileCleaner extends FileCleaner {
        override protected val fileRemover = mockFileRemover
      }

      lazy val fileCleaner: TestActorRef[TestFileCleaner] = TestActorRef(Props(new TestFileCleaner))
    }

    "call FileCleaner.deleteFile" in new FileCleaningScope {
      mockFileRemover.deleteFile(any) returns Future.unit
      fileCleaner ! Clean(1L)

      expectMsg(CleanComplete(1L))

      there was one(mockFileRemover).deleteFile(1L)
    }

    "notify requester when done" in new FileCleaningScope {
      val promise = Promise[Unit]()
      mockFileRemover.deleteFile(any) returns promise.future

      fileCleaner ! Clean(1L)
      expectNoMsg(Duration.Zero)
      promise.success(())

      expectMsg(CleanComplete(1L))
    }

    "notify requester on failure" in new FileCleaningScope {
      mockFileRemover.deleteFile(any) returns Future.failed(new Exception("something"))

      fileCleaner ! Clean(1L)

      expectMsg(CleanComplete(1L))
    }
  }
}


