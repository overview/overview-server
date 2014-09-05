package org.overviewproject.jobhandler.filegroup.task

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito

class DocumentIdGeneratorSpec extends Specification {

  
  "DocumentSetIdGenerator" should {
    
    "return increasing ids starting at 1, if no documents exist" in {
      val documentSetId = 1l
      val documentSetIdGenerator = new TestDocumentIdGenerator(documentSetId, 0)
      
      val ids = Seq.fill(3)(documentSetIdGenerator.nextId)
      val expectedIds = Seq.tabulate(3)(n => (documentSetId << 32) | (n + 1))
      
      ids must be equalTo expectedIds
      
    }
    
    "return the id after the highest currently existing id" in {
      val documentSetId = 1l
      val documentSetIdGenerator = new TestDocumentIdGenerator(documentSetId, 5)
      
      val ids = Seq.fill(3)(documentSetIdGenerator.nextId)
      val expectedIds = Seq.tabulate(3)(n => (documentSetId << 32) | (n + 6))
            
      ids must be equalTo expectedIds
    }
  }
  
}

class TestDocumentIdGenerator(override val documentSetId: Long, startId: Long) extends DocumentIdGenerator {
   override protected def existingDocumentCount = startId
}