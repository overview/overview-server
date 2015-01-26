package org.overviewproject.database


import org.overviewproject.database.Slick.simple._
import org.overviewproject.test.SlickSpecification
import org.overviewproject.test.SlickClientInSession
import org.overviewproject.models.tables.{ Files, TempDocumentSetFiles }

class TempFileDeleterSpec extends SlickSpecification {

  "TempFileDeleterSpec" should {
    
    "decrease file reference count" in new TempFileScope {
      
      tempFileDeleter.delete(documentSetId)
      
      val referenceCounts = Files.map(_.referenceCount).list
      
      referenceCounts must contain(0).exactly(numberOfFiles.times)
    }
    
    "delete temp_document_set_files" in new TempFileScope {
      todo
    }
  }
 
  
  trait TempFileScope extends DbScope {
    val documentSetId = 1l
    val numberOfFiles = 10
    val files = Seq.tabulate(numberOfFiles)(n => factory.file(name = s"file-$n"))
    
    files.foreach(f => factory.tempDocumentSetFile(documentSetId, f.id))
    
    val tempFileDeleter = new TestTempFileDeleter
  }
  
  class TestTempFileDeleter(implicit val session: Session) extends TempFileDeleter with SlickClientInSession
}