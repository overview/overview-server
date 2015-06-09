package org.overviewproject.background.filegroupcleanup

import org.overviewproject.test.DbSpecification

class DeletedFileGroupFinderSpec extends DbSpecification {
  
  "DeletedFileGroupFinder" should {
    
    "find deleted files" in new DeletedFileGroupScope {
      val foundIds = await(deletedFileGroupFinder.deletedFileGroupIds)
      foundIds must containTheSameElementsAs(deletedFileGroups.map(_.id))
    }
  }
  
  trait DeletedFileGroupScope extends DbScope {
    
    val deletedFileGroups = Seq.fill(2)(factory.fileGroup(deleted = true))
    val otherFileGroups = Seq.fill(2)(factory.fileGroup(deleted = false))
    
    val deletedFileGroupFinder = new TestDeletedFileGroupFinder
  }

  class TestDeletedFileGroupFinder extends DeletedFileGroupFinder with org.overviewproject.database.DatabaseProvider
}
