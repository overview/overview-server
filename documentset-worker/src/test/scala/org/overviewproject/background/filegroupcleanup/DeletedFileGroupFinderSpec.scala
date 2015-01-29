package org.overviewproject.background.filegroupcleanup

import org.overviewproject.database.Slick.simple._
import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }

class DeletedFileGroupFinderSpec extends SlickSpecification {
  
  "DeletedFileGroupFinder" should {
    
    "find deleted files" in new DeletedFileGroupScope {
      val foundIds = await(deletedFileGroupFinder.deletedFileGroupIds)
      foundIds must containTheSameElementsAs(deletedFileGroups.map(_.id))
    }
  }
  
  trait DeletedFileGroupScope extends DbScope {
    
    val deletedFileGroups = Seq.fill(10)(factory.fileGroup(deleted = true))
    val otherFileGroups = Seq.fill(10)(factory.fileGroup(deleted = false))
    
    val deletedFileGroupFinder = new TestDeletedFileGroupFinder
  }

  class TestDeletedFileGroupFinder(implicit val session: Session) 
    extends DeletedFileGroupFinder with SlickClientInSession
}