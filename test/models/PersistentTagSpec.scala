package models

import org.specs2.mock._
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

  
class PersistentTagSpec extends Specification with Mockito {
  implicit val unusedConnection: java.sql.Connection = null
  
  "PersistentTag" should {

    case class TestTag(id: Long, name: String, color: String) extends OverviewTag with TagColor {
      override def withName(newName: String): OverviewTag = this
      override def withColor(newColor: String): OverviewTag with TagColor = this
      override def withColor: Option[OverviewTag with TagColor] = Some(this)
      override def save {}
      override def delete {}
    }
    
    trait MockComponents extends Scope {
      val loader = mock[PersistentTagLoader]
      val parser = mock[DocumentListParser]
      val saver = mock[PersistentTagSaver]
      val name = "a tag"
      val color = "eeee11"
    }

    trait ExistingTag extends MockComponents  {
      val tagId = 32l
      val testTag = TestTag(tagId, name, color)
      val tag = PersistentTag(testTag, loader, parser)
    }

    trait DocumentsTagged extends ExistingTag  {
      val documentIds = Seq(1l, 2l, 3l)
      val dummyDocumentListData = Seq((0l, None))
      val dummyDocumentIdList = models.core.DocumentIdList(documentIds, 33l)
      
      loader loadDocumentList(tagId) returns dummyDocumentListData
      parser createDocumentIdList(dummyDocumentListData) returns dummyDocumentIdList
    }


    "should ask loader for number of documents with tag" in new ExistingTag {
      val dummyCount = 454
      loader countDocuments (tagId) returns dummyCount

      val count = tag.count

      there was one(loader).countDocuments(tagId)

      count must be equalTo (dummyCount)
    }

    "should ask loader for number of documents with tag per node" in new ExistingTag {
      val dummyCounts = Seq((1l, 5l), (2l, 3345l))
      val nodeIds = Seq(1l, 2l)

      loader countsPerNode (nodeIds, tagId) returns dummyCounts

      val counts = tag.countsPerNode(nodeIds)

      there was one(loader).countsPerNode(nodeIds, tagId)

      counts must be equalTo (dummyCounts)
    }

    "ask loader and parser to create document Id List" in new DocumentsTagged {
      val documentIdList = tag.documentIds

      there was one(loader).loadDocumentList(tagId)
      there was one(parser).createDocumentIdList(dummyDocumentListData)

      documentIdList must be equalTo(dummyDocumentIdList)
    }

    "load documents referenced by tag" in new DocumentsTagged {
      val documents = tag.loadDocuments

      there was one(loader).loadDocuments(documentIds)
    }

  }

}

