package models

import org.specs2.mutable.Specification

class DocumentListParserSpec extends Specification {

  "DocumentListParser" should {

    "create OverviewTags from tag data" in {
      val tagColor = Some("befab4")
      val tagId1 = 5l
      val tagId2 = 15l
      val name1 = "tag1"
      val name2 = "tag2"
      val doc1 = 10l
      val doc2 = 20l
      val docCount = 11l

      val tagData = List(
        (tagId1, name1, tagColor, docCount, Some(doc1)),
        (tagId1, name1, tagColor, docCount, Some(doc2)),
        (tagId2, name2, None, 0l, None))

      val parser = new DocumentListParser

      val tags = parser.createTags2(tagData)

      tags(0).id must be equalTo tagId1
      tags(0).name must be equalTo name1
      tags(0).color must be equalTo tagColor
      tags(0).documentIds.firstIds must haveTheSameElementsAs(Seq(doc1, doc2))
      tags(0).documentIds.totalCount must be equalTo docCount

      tags(1).id must be equalTo tagId2
      tags(1).name must be equalTo name2
      tags(1).color must beNone
      tags(1).documentIds.firstIds must beEmpty
      tags(1).documentIds.totalCount must be equalTo(0)
    }
  }

  "return empty list if given no data" in {
    val parser = new DocumentListParser
    val tags = parser.createTags2(Nil)
    
    tags must beEmpty
  }
}
