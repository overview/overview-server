package org.overviewproject.database

import org.overviewproject.test.DbSpecification
import org.overviewproject.models.tables.{ Files, TempDocumentSetFiles }

class TempFileDeleterSpec extends DbSpecification {

  "TempFileDeleterSpec" should {
    
    "decrease file reference count" in new TempFileScope {
      await(tempFileDeleter.delete(documentSetId))

      import databaseApi._
      blockingDatabase.seq(Files.map(_.referenceCount)) must contain(0).exactly(numberOfFiles.times)
    }
    
    "delete temp_document_set_files" in new TempFileScope {
      await(tempFileDeleter.delete(documentSetId))

      import databaseApi._
      blockingDatabase.length(TempDocumentSetFiles.filter(_.documentSetId === documentSetId)) must beEqualTo(0)
    }

    "only delete temp_document_set_files with specified document set id" in new TempFileScope {
      await(tempFileDeleter.delete(documentSetId))

      import databaseApi._
      blockingDatabase.length(TempDocumentSetFiles.filter(_.documentSetId === otherDocumentSetId)) must beGreaterThan(0)
    }
    
    "only decrease reference count to 0" in new InterruptedDeletionScope {
      await(tempFileDeleter.delete(documentSetId))
      
      import databaseApi._
      blockingDatabase.seq(Files.map(_.referenceCount)) must contain(0).exactly(numberOfFiles.times)
    }
  }
 
  trait TempFileScope extends DbScope {
    val documentSetId = 1l
    val otherDocumentSetId = 2l
    val numberOfFiles = 3
    def refCount = 1
    val files = Seq.tabulate(numberOfFiles)(n => factory.file(name = s"file-$n", referenceCount = refCount))

    files.foreach(f => factory.tempDocumentSetFile(documentSetId, f.id))
    factory.tempDocumentSetFile(otherDocumentSetId, 234l)
    
    val tempFileDeleter = new TempFileDeleter with DatabaseProvider
  }
  
  trait InterruptedDeletionScope extends TempFileScope {
    override def refCount = 0
  }
}
