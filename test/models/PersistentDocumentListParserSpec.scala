package models


import org.specs2.mutable.Specification

class PersistentDocumentListParserSpec extends Specification {
	
  "PersisteDocumentListParser" should {
    
    "Return empty list given empty input" in {
      
      val persistentDocumentListParser = new PersistentDocumentListParser()
      
      val documents = persistentDocumentListParser.createDocuments(Nil)
      
      documents must be empty
    }
  }
}