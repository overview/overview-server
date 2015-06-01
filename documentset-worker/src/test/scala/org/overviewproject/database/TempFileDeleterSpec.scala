package org.overviewproject.database

import slick.jdbc.JdbcBackend.Session

import org.overviewproject.test.{ DbSpecification, SlickClientInSession }
import org.overviewproject.models.tables.{ Files, TempDocumentSetFiles }

class TempFileDeleterSpec extends DbSpecification {

  "TempFileDeleterSpec" should {
    
    "decrease file reference count" in new TempFileScope {
      await(tempFileDeleter.delete(documentSetId))
      
      import org.overviewproject.database.Slick.simple._
      val referenceCounts = Files.map(_.referenceCount).list(session)
      
      referenceCounts must contain(0).exactly(numberOfFiles.times)
    }
    
    "delete temp_document_set_files" in new TempFileScope {
      await(tempFileDeleter.delete(documentSetId))
      
      import org.overviewproject.database.Slick.simple._
      val tempDocumentSets = TempDocumentSetFiles.filter(_.documentSetId === documentSetId).list(session)
      
      tempDocumentSets must beEmpty
    }
    
    "only delete temp_document_set_files with specified document set id" in new TempFileScope {
      await(tempFileDeleter.delete(documentSetId))
      
      import org.overviewproject.database.Slick.simple._
      val otherTempDocumentSets = TempDocumentSetFiles.filter(_.documentSetId === otherDocumentSetId).list(session)
      
      otherTempDocumentSets must not beEmpty
    }
    
    "only decrease reference count to 0" in new InterruptedDeletionScope {
      await(tempFileDeleter.delete(documentSetId))
      
      import org.overviewproject.database.Slick.simple._
      val referenceCounts = Files.map(_.referenceCount).list(session)
      
      referenceCounts must contain(0).exactly(numberOfFiles.times)
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
    
    val tempFileDeleter = new TestTempFileDeleter(session)
  }
  
  trait InterruptedDeletionScope extends TempFileScope {
    override def refCount = 0
  }
  
  class TestTempFileDeleter(val session: Session) extends TempFileDeleter with SlickClientInSession
}
