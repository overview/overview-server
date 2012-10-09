package models

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
  }
}
