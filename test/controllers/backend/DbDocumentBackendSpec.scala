package controllers.backend

import org.specs2.mock.Mockito
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import models.pagination.{Page,PageInfo,PageRequest}
import models.{SelectionLike,SelectionRequest}
import org.overviewproject.searchindex.{InMemoryIndexClient,IndexClient}
import org.overviewproject.models.Document

class DbDocumentBackendSpec extends DbBackendSpecification with Mockito {
  trait BaseScopeNoIndex extends DbScope {
    val backend = new TestDbBackend(session) with DbDocumentBackend {
      override val indexClient = mock[IndexClient]
    }
  }

  trait BaseScopeWithIndex extends DbScope {
    val testIndexClient: InMemoryIndexClient = new InMemoryIndexClient()
    val backend = new TestDbBackend(session) with DbDocumentBackend {
      override val indexClient: IndexClient = testIndexClient
    }

    override def after = {
      testIndexClient.close
      super.after
    }
  }

  trait CommonIndexScope extends BaseScopeWithIndex {
    val documentSet = factory.documentSet()
    val doc1 = factory.document(documentSetId=documentSet.id, title="c", text="foo bar baz oneandtwo oneandthree")
    val doc2 = factory.document(documentSetId=documentSet.id, title="a", text="moo mar maz oneandtwo twoandthree")
    val doc3 = factory.document(documentSetId=documentSet.id, title="b", text="noo nar naz oneandthree twoandthree")
    val documents = Seq(doc1, doc2, doc3)

    await(testIndexClient.addDocumentSet(documentSet.id))
    await(testIndexClient.addDocuments(documents.map(_.toDeprecatedDocument)))
    await(testIndexClient.refresh())
  }

  "DbDocumentBackendSpec" should {
    "#index" should {
      trait IndexScope extends CommonIndexScope {
        val selection = mock[SelectionLike]
        val pageRequest = PageRequest(0, 1000)
        selection.getDocumentIds(pageRequest) returns Future.successful(Page(Seq(doc1.id, doc2.id, doc3.id), PageInfo(pageRequest, 3)))
        lazy val ret = await(backend.index(selection, pageRequest))
      }

      "show all documents" in new IndexScope {
        ret.items.length must beEqualTo(3)
      }

      "sort documents by title" in new IndexScope {
        // XXX documents _should_ always be ordered such that they're in the
        // same order as selection.documentIds. That's not trivial in straight
        // SQL, and we don't have a feature that requires it yet; hence this
        // ordering.
        ret.items.map(_.id) must beEqualTo(Seq(doc2.id, doc3.id, doc1.id))
      }

      "return the correct pageInfo" in new IndexScope {
        ret.pageInfo.total must beEqualTo(3) // the list of IDs
        ret.pageInfo.offset must beEqualTo(0)
        ret.pageInfo.limit must beEqualTo(1000)
      }

      "work with 0 documents" in new IndexScope {
        selection.getDocumentIds(any) returns Future.successful(Page(Seq[Long]()))
        ret.items.length must beEqualTo(0)
        ret.pageInfo.total must beEqualTo(0)
      }
    }

    "#indexIds" should {
      trait IndexIdsScope extends CommonIndexScope {
        val documentIds: Seq[Long] = Seq()
        val tagIds: Seq[Long] = Seq()
        val nodeIds: Seq[Long] = Seq()
        val searchResultIds: Seq[Long] = Seq()
        val vizObjectIds: Seq[Long] = Seq()
        val tagged: Option[Boolean] = None
        val q: String = ""

        def request = SelectionRequest(
          documentSetId=documentSet.id,
          documentIds=documentIds,
          tagIds=tagIds,
          nodeIds=nodeIds,
          searchResultIds=searchResultIds,
          vizObjectIds=vizObjectIds,
          tagged=tagged,
          q=q
        )
        lazy val ret = await(backend.indexIds(request))
      }

      "show all documents by default" in new IndexIdsScope {
        ret.length must beEqualTo(3)
      }

      "sort documents by title" in new IndexIdsScope {
        ret must beEqualTo(Seq(doc2.id, doc3.id, doc1.id))
      }

      "search by q" in new IndexIdsScope {
        override val q = "moo"
        ret must beEqualTo(Seq(doc2.id))
      }

      "search by tagIds" in new IndexIdsScope {
        val tag = factory.tag(documentSetId=documentSet.id)
        val dt1 = factory.documentTag(doc1.id, tag.id)
        val dt2 = factory.documentTag(doc2.id, tag.id)

        override val tagIds = Seq(tag.id)
        ret must beEqualTo(Seq(doc2.id, doc1.id))
      }

      "take the union when given multiple tag IDs" in new IndexIdsScope {
        val tag1 = factory.tag(documentSetId=documentSet.id)
        val tag2 = factory.tag(documentSetId=documentSet.id)
        val dt1 = factory.documentTag(doc1.id, tag1.id)
        val dt2 = factory.documentTag(doc2.id, tag2.id)

        override val tagIds = Seq(tag1.id, tag2.id)
        ret must beEqualTo(Seq(doc2.id, doc1.id))
      }

      "intersect results from ElasticSearch and Postgres" in new IndexIdsScope {
        val tag = factory.tag(documentSetId=documentSet.id)
        val dt1 = factory.documentTag(doc1.id, tag.id)
        val dt2 = factory.documentTag(doc2.id, tag.id)

        override val tagIds = Seq(tag.id)
        override val q = "oneandthree"
        ret must beEqualTo(Seq(doc1.id))
      }

      "search by nodeIds" in new IndexIdsScope {
        val node = factory.node()
        val nd1 = factory.nodeDocument(node.id, doc1.id)
        val nd2 = factory.nodeDocument(node.id, doc2.id)

        override val nodeIds = Seq(node.id)
        ret must beEqualTo(Seq(doc2.id, doc1.id))
      }

      "intersect two Postgres filters" in new IndexIdsScope {
        val node = factory.node()
        val nd1 = factory.nodeDocument(node.id, doc1.id)
        val nd2 = factory.nodeDocument(node.id, doc2.id)

        val tag = factory.tag(documentSetId=documentSet.id)
        val dt1 = factory.documentTag(doc1.id, tag.id)
        val dt2 = factory.documentTag(doc3.id, tag.id)

        override val nodeIds = Seq(node.id)
        override val tagIds = Seq(tag.id)
        ret must beEqualTo(Seq(doc1.id))
      }

      "search by searchResultIds" in new IndexIdsScope {
        val sr = factory.searchResult(documentSetId=documentSet.id)
        val dsr1 = factory.documentSearchResult(doc1.id, sr.id)
        val dsr2 = factory.documentSearchResult(doc2.id, sr.id)

        override val searchResultIds = Seq(sr.id)
        ret must beEqualTo(Seq(doc2.id, doc1.id))
      }

      "search by vizObjectIds" in new IndexIdsScope {
        val viz = factory.viz(documentSetId=documentSet.id)
        val obj = factory.vizObject(vizId=viz.id)
        val dvo1 = factory.documentVizObject(doc1.id, obj.id)
        val dvo2 = factory.documentVizObject(doc2.id, obj.id)

        override val vizObjectIds = Seq(obj.id)
        ret must beEqualTo(Seq(doc2.id, doc1.id))
      }

      "search by tagged=false" in new IndexIdsScope {
        val tag = factory.tag(documentSetId=documentSet.id)
        val dt1 = factory.documentTag(doc1.id, tag.id)
        val dt2 = factory.documentTag(doc2.id, tag.id)

        override val tagged = Some(false)
        ret must beEqualTo(Seq(doc3.id))
      }

      "search by tagged=true" in new IndexIdsScope {
        val tag = factory.tag(documentSetId=documentSet.id)
        val dt1 = factory.documentTag(doc1.id, tag.id)
        val dt2 = factory.documentTag(doc2.id, tag.id)

        override val tagged = Some(true)
        ret must beEqualTo(Seq(doc2.id, doc1.id))
      }
    }

    "#show" should {
      trait ShowScope extends BaseScopeNoIndex {
        val documentSet = factory.documentSet()
        val document = factory.document(documentSetId=documentSet.id, title="title", text="text")

        val documentSetId = documentSet.id
        val documentId = document.id

        lazy val ret = await(backend.show(documentSetId, documentId))
      }

      "show a document" in new ShowScope {
        ret must beSome { d: Document =>
          d.id must beEqualTo(document.id)
          d.title must beEqualTo(document.title)
          d.text must beEqualTo(document.text)
        }
      }

      "not show a document with the wrong document set ID" in new ShowScope {
        override val documentSetId = documentSet.id + 1L
        ret must beNone
      }

      "not show a document with the wrong ID" in new ShowScope {
        override val documentId = document.id + 1L
        ret must beNone
      }
    }
  }
}
