package models

import org.specs2.mock._
import org.specs2.mutable.Specification

class DocumentLoaderSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null
  
  "DocumentLoader" should {
    "call loader and parser to create optional document" in {
      val loader = mock[DocumentDataLoader]
      val parser = mock[DocumentListParser]
      
      val dummyDocumentData = Some((10l, "title", "documentCloudId"))
      loader loadDocument(17l) returns dummyDocumentData
      parser createDocuments(dummyDocumentData.toList, Nil, Nil) returns 
        Seq(core.Document(10l, "title", "documentCloudId", null))
      
      val documentLoader = new DocumentLoader(loader, parser)
      
      documentLoader.load(17l)
      
      there was one(loader).loadDocument(17l)
      there was one(parser).createDocuments(dummyDocumentData.toList, Nil, Nil)
    }
    
    "be constructable with default loader and parser" in {
      val documentLoader = new DocumentLoader()
      
      success
    }
  }
}
