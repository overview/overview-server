package models

import org.specs2.mock._
import org.specs2.mutable.Specification

class DocumentLoaderSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null
  
  "DocumentLoader" should {
    "call loader and parser to create optional document" in {
      val loader = mock[DocumentDataLoader]
      val parser = mock[DocumentDataParser]
      
      val dummyDocumentData = Some((10l, "title", "textUrl", "viewUrl"))
      loader loadDocument(17l) returns dummyDocumentData
      
      val documentLoader = new DocumentLoader(loader, parser)
      
      documentLoader.load(17l)
      
      there was one(loader).loadDocument(17l)
      there was one(parser).createDocument(dummyDocumentData)
    }
    
    "be constructable with default loader and parser" in {
      val documentLoader = new DocumentLoader()
      
      success
    }
  }
}