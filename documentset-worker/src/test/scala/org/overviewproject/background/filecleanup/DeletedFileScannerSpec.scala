package org.overviewproject.background.filecleanup



import org.overviewproject.database.Slick.simple._
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession

class DeletedFileScannerSpec extends SlickSpecification {

  "DeletedFileScanner" should {
    
    "find deleted files" in new DeletedFilesScope {
      val fileIds = await(deletedFileScanner.deletedFileIds)
      
      fileIds must containTheSameElementsAs(deletedFiles.map(_.id))
    }
  }

  trait DeletedFilesScope extends DbScope {
    val numberOfDeletedFiles = 10
    val deletedFiles = Seq.fill(numberOfDeletedFiles)(factory.file(referenceCount = 0))
    
    val numberOfFiles = 10
    val existingFiles = Seq.fill(numberOfFiles)(factory.file(referenceCount = 1))
    
    val deletedFileScanner = new TestDeletedFileScanner
  }
  
  class TestDeletedFileScanner(implicit val session: Session) extends DeletedFileScanner with SlickClientInSession
}