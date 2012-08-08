package models


import org.specs2.mutable.Specification


class PersistentDocumentListParserSpec extends Specification {
	
  "PersisteDocumentListParser" should {
    
    "return empty list given empty input" in {
      val persistentDocumentListParser = new DocumentListParser()
      
      val documents = persistentDocumentListParser.createDocuments(Nil, Nil)
      
      documents must be empty
    }
    
   "create documents from data" in {
     val documentData = List((10l, "title1", "textUrl1", "viewUrl1"),
    		 				 (20l, "title2", "textUrl2", "viewUrl2"),
    		 				 (30l, "title3", "textUrl3", "viewUrl3")
     )
     val persistentDocumentListParser = new DocumentListParser()
     
     val documents = persistentDocumentListParser.createDocuments(documentData, Nil)
     
     val ids = documents.map(_.id)
     val titles = documents.map(_.title)
     val textUrl = documents.map(_.textUrl)
     val viewUrl = documents.map(_.viewUrl)
     
     ids must haveTheSameElementsAs(List(10l, 20l, 30l))
     titles must haveTheSameElementsAs(List("title1", "title2", "title3"))
     textUrl must haveTheSameElementsAs(List("textUrl1", "textUrl2", "textUrl3"))
     viewUrl must haveTheSameElementsAs(List("viewUrl1", "viewUrl2", "viewUrl3"))
   }
 }
}