package com.overviewdocs.background.filecleanup

import akka.actor._
import akka.testkit.TestActorRef
import FileCleanerProtocol._
import org.specs2.mock.Mockito
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import scala.concurrent.duration.Duration
import scala.concurrent.Promise

import com.overviewdocs.test.ActorSystemContext

class FileCleanerSpec extends Specification with Mockito {
  sequential

  "FileCleaner" should {

    "start cleaning" in new FileCleaningScope {
      fileCleaner ! Clean(fileId)
      deleteFile.success(())
      
      expectMsg(CleanComplete(fileId))
      
      there was one(mockFileRemover).deleteFile(fileId)
    }

    "notify requester when done" in new FileCleaningScope {
      fileCleaner ! Clean(fileId)
      expectNoMsg(Duration.Zero)
      deleteFile.success(())

      expectMsg(CleanComplete(fileId))
    }
    
    "notify requester on failure" in new FileCleaningScope {
      fileCleaner ! Clean(fileId)
      val error = new Exception("something baaad is happening in Oz")
      deleteFile.failure(error)
      
      expectMsg(CleanComplete(fileId))
    }
  }

  abstract class FileCleaningScope extends ActorSystemContext with Before {
    val fileId = 10l
    var deleteFile: Promise[Unit] = _
    var fileCleaner: TestActorRef[TestFileCleaner] = _
    
    def mockFileRemover = fileCleaner.underlyingActor.mockFileRemover
    
    override def before = {
      deleteFile = Promise[Unit]()
      fileCleaner = TestActorRef(Props(new TestFileCleaner))
      
      fileCleaner.underlyingActor.mockFileRemover.deleteFile(any) returns deleteFile.future
    }
  }

  class TestFileCleaner extends FileCleaner {
    override protected val fileRemover = smartMock[FileRemover]
    def mockFileRemover = fileRemover
  }

}


