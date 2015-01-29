package org.overviewproject.background.filegroupcleanup

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import org.specs2.mock.Mockito
import scala.concurrent.Promise
import akka.actor.Props
import akka.testkit.TestActorRef

import FileGroupCleanerProtocol._

class FileGroupCleanerSpec extends Specification with Mockito {

  "FileGroupCleaner" should {
    
    "start cleaning" in new FileGroupCleanerScope {
      fileGroupCleaner ! Clean(fileGroupId)
      
      there was one(mockFileGroupRemover).remove(fileGroupId)
    }
    
    "notify requester when done" in new FileGroupCleanerScope {
      fileGroupCleaner ! Clean(fileGroupId)
      
      fileGroupRemoved.success(())
      
      expectMsg(CleanComplete(fileGroupId))
    }
    
    "notify requester on failure" in new FileGroupCleanerScope {
      fileGroupCleaner ! Clean(fileGroupId)
      
      fileGroupRemoved.failure(new Exception("fail"))
      
      expectMsg(CleanComplete(fileGroupId))
    }
  }
  
  abstract class FileGroupCleanerScope extends ActorSystemContext with Before {
    
    val fileGroupId = 10l
    var fileGroupRemoved: Promise[Unit] = _
    var fileGroupCleaner: TestActorRef[TestFileGroupCleaner] = _
    
    def mockFileGroupRemover = fileGroupCleaner.underlyingActor.mockFileGroupRemover
    
    override def before = {
      fileGroupCleaner = TestActorRef(Props(new TestFileGroupCleaner))
      fileGroupRemoved = Promise()
      mockFileGroupRemover.remove(any) returns fileGroupRemoved.future
    }
  }
  
  class TestFileGroupCleaner extends FileGroupCleaner {
    override protected val fileGroupRemover = smartMock[FileGroupRemover]
    def mockFileGroupRemover = fileGroupRemover
    
  }
}