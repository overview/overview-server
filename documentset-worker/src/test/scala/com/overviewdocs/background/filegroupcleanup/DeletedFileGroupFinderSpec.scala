package com.overviewdocs.background.filegroupcleanup

import com.overviewdocs.test.DbSpecification

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
    
    val deletedFileGroupFinder = DeletedFileGroupFinder()
  }
}
