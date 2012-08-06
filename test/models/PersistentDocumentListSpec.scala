package models

import org.specs2.mock._
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class PersistentDocumentListSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null

  "PersistDocumentList" should {

    val nodeIds = Seq(1l, 2l, 4l)
    val documentIds = Seq(5l, 6l)

    trait MockComponents extends Scope {
      val loader = mock[PersistentDocumentListDataLoader]
      val parser = mock[PersistentDocumentListParser]

      val persistentDocumentList =
        new PersistentDocumentList(nodeIds, documentIds, loader, parser)
    }

    trait MockSaver extends Scope {
      val tagId = 34l;
      val saver = mock[PersistentDocumentListDataSaver]

      val persistentDocumentList =
        new PersistentDocumentList(nodeIds, documentIds, saver = saver)
    }

    "extract ids from input strings" in new MockComponents {
      val dummyDocumentData = Nil

      loader loadSelectedDocumentSlice (nodeIds, documentIds, 0, 10) returns
        dummyDocumentData

      val documents = persistentDocumentList.loadSlice(0, 10)

      there was one(loader).loadSelectedDocumentSlice(nodeIds, documentIds, 0, 10)
    }

    "call loader and parser to create Documents" in new MockComponents {
      val dummyDocumentData = Nil
      val dummyDocuments = List(core.Document(1l, "title", "text", "view"))

      loader loadSelectedDocumentSlice (nodeIds, documentIds, 0, 10) returns
        dummyDocumentData
      parser createDocuments (dummyDocumentData) returns dummyDocuments

      val documents = persistentDocumentList.loadSlice(0, 10)

      there was one(loader).loadSelectedDocumentSlice(nodeIds, documentIds, 0, 10)
      there was one(parser).createDocuments(dummyDocumentData)

      documents must be equalTo (dummyDocuments)
    }

    "compute offset and limit of slice" in new MockComponents {
      val dummyDocumentData = Nil

      loader loadSelectedDocumentSlice (nodeIds, documentIds, 3, 4) returns
        dummyDocumentData

      val documents = persistentDocumentList.loadSlice(3, 7)

      there was one(loader).loadSelectedDocumentSlice(nodeIds, documentIds, 3, 4)
    }

    "call loader to get selection count" in new MockComponents {
      val expectedCount = 256l

      loader.loadCount(nodeIds, documentIds) returns expectedCount

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
      val persistentDocumentList = new PersistentDocumentList(Nil, Nil)

      success
    }

    "call saver to add a tag" in new MockSaver {
      val dummyCount = 19l

      saver.addTag(tagId, nodeIds, documentIds) returns dummyCount

      val count = persistentDocumentList.addTag(tagId)

      there was one(saver).addTag(tagId, nodeIds, documentIds)
      count must be equalTo dummyCount
    }

    "call saver to remove a tag" in new MockSaver {
      val dummyCount = 19l

      saver.removeTag(tagId, nodeIds, documentIds) returns dummyCount

      val count = persistentDocumentList.removeTag(tagId)

      there was one(saver).removeTag(tagId, nodeIds, documentIds)
      count must be equalTo dummyCount
    }

  }

}