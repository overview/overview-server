package models

import org.specs2.mutable.Specification

class DocumentDataParserSpec extends Specification {

  "DocumentDataParser" should {
    
    "return optional Document given data" in {
      val documentData = Option((10l, "title", "textUrl", "viewUrl"))
      
      val documentDataParser = new DocumentDataParser()
      
      val createdDocument = documentDataParser.createDocument(documentData)
      createdDocument must beSome
      val document = createdDocument.get

      document must be equalTo(core.Document(10l, "title", "textUrl", "viewUrl"))
    }
    
    "return None given None (nothing will come from nothing)" in {
      val documentDataParser = new DocumentDataParser()
      
      val createdDocument = documentDataParser.createDocument(None)
      
      createdDocument must beNone
    }
  }
}