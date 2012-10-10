package models

import models.core.DocumentIdList
import org.specs2.mutable.Specification

class DocumentListParserSpec extends Specification {

  "DocumentListParser" should {

    "create an empty DocumentIdList given no data" in {
      val parser = new DocumentListParser

      val emptyDocumentIdList = parser.createDocumentIdList(Nil)

      emptyDocumentIdList.firstIds must beEmpty
      emptyDocumentIdList.totalCount must be equalTo (0)
    }

    "create an empty DocumentIdList given data with 0 count" in {
      val parser = new DocumentListParser
      val emptyData = Seq((0l, None))

      val emptyDocumentIdList = parser.createDocumentIdList(emptyData)

      emptyDocumentIdList.firstIds must beEmpty
      emptyDocumentIdList.totalCount must be equalTo (0)
    }
    
    "create DocumentIdList from data" in {
      val docCount = 234l
      val firstDocumentIds = Seq(10l, 20l, 30l)
      val documentListData = firstDocumentIds.map(d => (docCount, Some(d)))
      val parser = new DocumentListParser

      val documentIdList = parser.createDocumentIdList(documentListData)

      documentIdList.firstIds must haveTheSameElementsAs(firstDocumentIds)
      documentIdList.totalCount must be equalTo (docCount)
    }

    "create Tags from tuples" in {
      val tagColor = Some("befab4")
      val tagData = List(
	(5l, "tag1", 11l, Some(10l), tagColor),
	(5l, "tag1", 11l, Some(20l), tagColor),
        (15l, "tag2", 0l, None, None))
                         
      val parser = new DocumentListParser()
      val tags = parser.createTags(tagData)

      case class TestTag(id: Long, name: String, color: Option[String], documentIds: DocumentIdList) extends PersistentTagInfo
      val expectedTags = List[PersistentTagInfo](
	TestTag(5l, "tag1", tagColor, core.DocumentIdList(Seq(10l, 20l), 11)),
    	TestTag(15l, "tag2", None, core.DocumentIdList(Nil, 0)))

      def equalTags(a: PersistentTagInfo, b: PersistentTagInfo): Boolean =
	a.id == b.id && a.name == b.name && a.color == b.color && a.documentIds == b.documentIds
      
      tags must haveTheSameElementsAs(expectedTags) ^^ equalTags _
    }

  }
}
