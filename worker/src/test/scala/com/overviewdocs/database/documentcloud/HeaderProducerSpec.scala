package com.overviewdocs.documentcloud

import scala.concurrent.Future

import com.overviewdocs.test.DbSpecification

class HeaderProducerSpec extends DbSpecification {
  import HeaderProducer._

  class BaseScope extends DbScope {
    val documentSet = factory.documentSet()
    val nIdLists = 1
    val splitPages = false
    lazy val dcImport = factory.documentCloudImport(documentSetId=documentSet.id, nIdListsTotal=Some(nIdLists), splitPages=splitPages)
    lazy val subject = new HeaderProducer(dcImport)

    def header(id: Long, dcId: String, title: String, pageNumber: Option[Int], textUrl: String, access: String) = {
      DocumentCloudDocumentHeader((documentSet.id << 32) + id, documentSet.id, dcId, title, pageNumber, textUrl, access)
    }

    def createIdList(pageNumber: Int, idsString: String) = {
      factory.documentCloudImportIdList(
        documentCloudImportId=dcImport.id,
        pageNumber=pageNumber,
        idsString=idsString,
        nDocuments=0,
        nPages=0
      )
    }
  }

  "HeaderProducer" should {
    "produce an empty result" in new BaseScope {
      createIdList(0, "")
      subject.next must beEqualTo(End).await
    }

    "produce a header" in new BaseScope {
      createIdList(0, "id\u001ftitle\u001f2\u001fhttp://full\u001fhttp://page{page}\u001faccess")
      subject.next must beEqualTo(Fetch(header(0L, "id", "title", None, "http://full", "access"))).await
      subject.next must beEqualTo(End).await
    }

    "iterate over all headers in an IdList" in new BaseScope {
      createIdList(0, "id\u001ftitle\u001f2\u001fhttp://full\u001fhttp://page{page}\u001faccess\u001eid2\u001ftitle2\u001f3\u001fhttp://full2\u001fhttp://page2{page}\u001faccess2")
      subject.next.flatMap(_ => subject.next) must beEqualTo(Fetch(
        header(1L, "id2", "title2", None, "http://full2", "access2")
      )).await
    }

    "iterate over pages in an IdList for multi-page documents" in new BaseScope {
      override val splitPages = true
      createIdList(0, "id\u001ftitle\u001f2\u001fhttp://full\u001fhttp://page{page}\u001faccess\u001eid2\u001ftitle2\u001f1\u001fhttp://full2\u001fhttp://pagetwo{page}\u001faccess2")
      subject.next must beEqualTo(Fetch(header(0L, "id#p1", "title", Some(1), "http://page1", "access"))).await
      subject.next must beEqualTo(Fetch(header(1L, "id#p2", "title", Some(2), "http://page2", "access"))).await
      // Single-page documents don't get split
      subject.next must beEqualTo(Fetch(header(2L, "id2", "title2", None, "http://full2", "access2"))).await
      subject.next must beEqualTo(End).await
    }

    "iterate over IdLists" in new BaseScope {
      override val nIdLists = 2
      createIdList(0, "id\u001ftitle\u001f2\u001fhttp://full\u001fhttp://page{page}\u001faccess")
      createIdList(1, "id2\u001ftitle2\u001f1\u001fhttp://full2\u001fhttp://pagetwo{page}\u001faccess2")
      subject.next must beEqualTo(Fetch(header(0L, "id", "title", None, "http://full", "access"))).await
      subject.next must beEqualTo(Fetch(header(1L, "id2", "title2", None, "http://full2", "access2"))).await
      subject.next must beEqualTo(End).await
    }

    "stop" in new BaseScope {
      createIdList(0, "id\u001ftitle\u001f2\u001fhttp://full\u001fhttp://page{page}\u001faccess")
      createIdList(1, "id2\u001ftitle2\u001f1\u001fhttp://full2\u001fhttp://pagetwo{page}\u001faccess2")
      subject.next must beAnInstanceOf[Fetch].await
      subject.stop
      subject.next must beEqualTo(End).await
    }
  }
}
