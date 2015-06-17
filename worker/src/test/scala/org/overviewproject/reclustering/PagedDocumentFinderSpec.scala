package org.overviewproject.reclustering

import org.overviewproject.persistence.orm.Schema._
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification

class PagedDocumentFinderSpec extends DbSpecification {
  "PagedDocumentFinder" should {

    trait DocumentContext extends DbScope {
      val documentIds = Seq(4l, 6l, 2l, 1l, 7l)
      val pageSize = 2

      val documentSet = factory.documentSet()
      documentIds.foreach(id => factory.document(documentSetId=documentSet.id, id=id))

      protected def tagId: Option[Long] = None
      lazy val subject = PagedDocumentFinder(documentSet.id, tagId, pageSize)
    }

    trait TaggedDocumentContext extends DocumentContext {
      val tag = factory.tag(documentSetId=documentSet.id)

      documentIds.drop(1).take(3).foreach(docId => factory.documentTag(docId, tag.id))
      override def tagId = Some(tag.id)
    }

    "return documents by page" in new DocumentContext {
      subject.findDocuments(1).map(_.id) must beEqualTo(Seq(1L, 2L))
      subject.findDocuments(2).map(_.id) must beEqualTo(Seq(4L, 6L))
      subject.findDocuments(3).map(_.id) must beEqualTo(Seq(7L))
      subject.findDocuments(4).map(_.id) must beEqualTo(Seq())
    }

    "return total count" in new DocumentContext {
      subject.numberOfDocuments must beEqualTo(5)
    }

    "return only tagged documents when given tag" in new TaggedDocumentContext {
      subject.findDocuments(1).map(_.id) must beEqualTo(Seq(1L, 2L))
      subject.findDocuments(2).map(_.id) must beEqualTo(Seq(6L))
    }
    
    "count only tagged documents when given tag" in new TaggedDocumentContext {
      subject.numberOfDocuments must beEqualTo(3)
    }
  }
}
