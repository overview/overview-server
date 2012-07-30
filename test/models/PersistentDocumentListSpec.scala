package models

import org.specs2.mock._
import org.specs2.mutable.Specification

class PersistentDocumentListSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null
  
  "PersistDocumentList" should {
    
    "extract ids from input strings" in {
      val loader = mock[PersistentDocumentListDataLoader]
      val parser = mock[PersistentDocumentListParser]
      val dummyDocumentData = Nil
      
      loader loadSelectedDocumentSlice(List(1, 2, 4), List(5, 6), 0, 10) returns dummyDocumentData
      
      val persistentDocumentList = 
        new PersistentDocumentList("1, 2, 4", "5, 6", loader, parser)

      val documents = persistentDocumentList.loadSlice(0, 10)
      
      there was one(loader).loadSelectedDocumentSlice(List(1, 2, 4), List(5, 6), 0, 10)
    }
    
    "be constructable with default loader and parser" in {
      val persistentDocumentList = new PersistentDocumentList("", "")
      
      success
    }
  }

}