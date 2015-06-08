package org.overviewproject.background.filecleanup

import org.overviewproject.test.DbSpecification
import org.overviewproject.database.DatabaseProvider

/** Find [[File]] ids where `referenceCount == 0` */
class DeletedFileFinderSpec extends DbSpecification {

  "DeletedFileFinder" should {
    "find deleted files" in new DeletedFilesScope {
      val fileIds = await(deletedFileFinder.deletedFileIds)
      
      fileIds must containTheSameElementsAs(deletedFiles.map(_.id))
    }
  }

  trait DeletedFilesScope extends DbScope {
    val numberOfDeletedFiles = 2
    val deletedFiles = Seq.fill(numberOfDeletedFiles)(factory.file(referenceCount = 0))
    
    val numberOfFiles = 2
    val existingFiles = Seq.fill(numberOfFiles)(factory.file(referenceCount = 1))
    
    val deletedFileFinder = new DeletedFileFinder with DatabaseProvider
  }
}
