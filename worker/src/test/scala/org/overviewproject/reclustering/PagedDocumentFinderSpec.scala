package org.overviewproject.reclustering

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.{ Document, DocumentSet }
import org.overviewproject.persistence.orm.Schema._
import org.overviewproject.tree.orm.Tag
import org.overviewproject.tree.orm.DocumentTag

class PagedDocumentFinderSpec extends DbSpecification {

  step(setupDb)

  "PagedDocumentFinder" should {

    trait DocumentContext extends DbTestContext {
      val documentIds = Seq(4l, 6l, 2l, 1l, 7l, 9l, 3l, 5l, 8l)
      val pageSize = 3

      var documentSet: DocumentSet = _
      var pagedDocumentFinder: PagedDocumentFinder = _

      override def setupWithDb = {
        documentSet = documentSets.insertOrUpdate(DocumentSet(title = "PagedDocumentFinderSpec"))
        val docs = documentIds.map(n => Document(documentSet.id, id = n))
        documents.insert(docs)

        pagedDocumentFinder = PagedDocumentFinder(documentSet.id, tagId, pageSize)
      }

      protected def tagId: Option[Long] = None
    }

    trait TaggedDocumentContext extends DocumentContext {
      var tag: Tag = _

      override def setupWithDb = {
        super.setupWithDb

        documentIds.take(5).foreach { id =>
          documentTags.insert(DocumentTag(id, tag.id))
        }
      }

      override protected def tagId: Option[Long] = {
        tag = tags.insert(Tag(documentSet.id, "tag", "ababab"))

        Some(tag.id)
      }
    }

    "return documents by page" in new DocumentContext {
      val pagedDocumentIds = documentIds.sorted.grouped(pageSize).toSeq

      for (p <- 1 to 3) yield {
        val page = pagedDocumentFinder.findDocuments(p)
        page.map(_.id) must be equalTo (pagedDocumentIds(p - 1))
      }
    }

    "return total count" in new DocumentContext {
      pagedDocumentFinder.numberOfDocuments must be equalTo (documentIds.size)
    }

    "return only tagged documents when given tag" in new TaggedDocumentContext {
      def getResults(page: Int): Seq[Document] = {
        val pageDocs: Seq[Document] = pagedDocumentFinder.findDocuments(page)

        if (pageDocs.isEmpty) Seq.empty
        else pageDocs ++ getResults(page + 1)
      }

      getResults(1).map(_.id) must containTheSameElementsAs(documentIds.take(5))
    }
    
    "count only tagged documents when given tag" in new TaggedDocumentContext {
      pagedDocumentFinder.numberOfDocuments must be equalTo(5)
    }
  }

  step(shutdownDb)
}