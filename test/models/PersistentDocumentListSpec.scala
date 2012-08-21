package models

import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class PersistentDocumentListSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null

  "PersistDocumentList" should {

    val nodeIds = Seq(1l, 2l, 4l)
    val documentIds = Seq(5l, 6l)
    val tagIds = Seq(7l, 8l, 9l, 10l)
    val documentSetId = 54l
    
    trait MockComponents extends Scope {
      val loader = mock[PersistentDocumentListDataLoader]
      val parser = mock[DocumentListParser]

      val persistentDocumentList =
        new PersistentDocumentList(documentSetId, nodeIds, tagIds, documentIds, 
        							loader, parser)
    }

    trait MockSaver extends Scope {
      val tagId = 34l;
      val saver = mock[PersistentDocumentListDataSaver]
      val documentSetId = 54l
      
      val persistentDocumentList =
        new PersistentDocumentList(documentSetId, nodeIds, tagIds, documentIds,
        						   saver = saver)
    }

    "extract ids from input strings" in new MockComponents {
      val dummyDocumentData = Nil

      loader loadSelectedDocumentSlice (documentSetId, 
                                        nodeIds, tagIds, documentIds, 0, 10) returns
        dummyDocumentData

      val documents = persistentDocumentList.loadSlice(0, 10)

      there was one(loader).loadSelectedDocumentSlice(documentSetId, nodeIds, tagIds,
                                                      documentIds, 0, 10)
    }

    "call loader and parser to create Documents" in new MockComponents {
      val dummyDocumentData = List((1l, "title", "text", "view"))
      val dummyDocumentTagData = List((1l, 5l), (1l, 15l))
      val dummyDocuments = List(core.Document(1l, "title", "text", "view", Seq(4l)))

      loader loadSelectedDocumentSlice(documentSetId, nodeIds, tagIds, documentIds, 
    		  					       0, 10) returns dummyDocumentData
      loader loadDocumentTags(List(1l)) returns dummyDocumentTagData
      parser createDocuments(dummyDocumentData, dummyDocumentTagData) returns dummyDocuments

      val documents = persistentDocumentList.loadSlice(0, 10)

      there was one(loader).loadSelectedDocumentSlice(documentSetId, nodeIds, tagIds,
                                                      documentIds, 0, 10)
      there was one(parser).createDocuments(dummyDocumentData, dummyDocumentTagData)

      documents must be equalTo (dummyDocuments)
    }

    "compute offset and limit of slice" in new MockComponents {
      val dummyDocumentData = Nil

      loader loadSelectedDocumentSlice (documentSetId, nodeIds, tagIds, documentIds, 
                                        3, 4) returns dummyDocumentData

      val documents = persistentDocumentList.loadSlice(3, 7)

      there was one(loader).loadSelectedDocumentSlice(documentSetId, nodeIds, tagIds,
                                                      documentIds, 3, 4)
    }

    "call loader to get selection count" in new MockComponents {
      val expectedCount = 256l

      loader.loadCount(documentSetId, nodeIds, tagIds, documentIds) returns expectedCount

      val count = persistentDocumentList.loadCount()

      count must be equalTo (expectedCount)
    }

    "throw exception if start of slice is < 0" in new MockComponents {
      persistentDocumentList.loadSlice(-3, 3) must throwAn[IllegalArgumentException]
    }

    "throw exception if start > end" in new MockComponents {
      persistentDocumentList.loadSlice(5, 3) must throwAn[IllegalArgumentException]
    }

    "throw exception if start == end" in new MockComponents {
      persistentDocumentList.loadSlice(5, 5) must throwAn[IllegalArgumentException]
    }

    "be constructable with default loader and parser" in {
      val persistentDocumentList = new PersistentDocumentList(5l, Nil, Nil, Nil)

      success
    }

    "call saver to add a tag" in new MockSaver {
      val dummyCount = 19l

      saver.addTag(tagId, documentSetId, nodeIds, tagIds, documentIds) returns dummyCount

      val count = persistentDocumentList.addTag(tagId)

      there was one(saver).addTag(tagId, documentSetId, nodeIds, tagIds, documentIds)
      count must be equalTo dummyCount
    }

    "call saver to remove a tag" in new MockSaver {
      val dummyCount = 19l

      saver.removeTag(tagId, documentSetId, nodeIds, tagIds, documentIds) returns dummyCount

      val count = persistentDocumentList.removeTag(tagId)

      there was one(saver).removeTag(tagId, documentSetId, nodeIds, tagIds, documentIds)
      count must be equalTo dummyCount
    }

  }

}