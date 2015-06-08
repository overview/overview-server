package controllers.backend

import org.specs2.mock.Mockito
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import slick.jdbc.JdbcBackend.Session

import models.pagination.{Page,PageInfo,PageRequest}
import models.{Selection,SelectionRequest}
import org.overviewproject.models.Document
import org.overviewproject.query.{PhraseQuery,Query}
import org.overviewproject.searchindex.{InMemoryIndexClient,IndexClient}
import org.overviewproject.test.SlickClientInSession
import org.overviewproject.util.SortedDocumentIdsRefresher

class DbDocumentBackendSpec extends DbBackendSpecification with Mockito {
  class InSessionSortedDocumentIdsRefresher(val session: Session)
    extends SortedDocumentIdsRefresher with SlickClientInSession

  trait BaseScope extends DbScope {
    val refresher = new InSessionSortedDocumentIdsRefresher(session)
  }

  trait BaseScopeNoIndex extends BaseScope {
    val backend = new DbDocumentBackend with org.overviewproject.database.DatabaseProvider {
      override val indexClient = mock[IndexClient]
    }
  }

  trait BaseScopeWithIndex extends BaseScope {
    val testIndexClient: InMemoryIndexClient = new InMemoryIndexClient()
    val backend = new DbDocumentBackend with org.overviewproject.database.DatabaseProvider {
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
    await(testIndexClient.addDocuments(documents))
    await(testIndexClient.refresh())
    await(refresher.refreshDocumentSet(documentSet.id))
  }

  "DbDocumentBackendSpec" should {
    "#index" should {
      trait IndexScope extends CommonIndexScope {
        val selection = mock[Selection]
        val pageRequest = PageRequest(0, 1000)
        val includeText = false
        selection.getDocumentIds(pageRequest) returns Future.successful(Page(Seq(doc2.id, doc3.id, doc1.id), PageInfo(pageRequest, 3)))
        lazy val ret = await(backend.index(selection, pageRequest, includeText))
      }

      "show all documents" in new IndexScope {
        ret.items.length must beEqualTo(3)
      }

      "sort documents by title" in new IndexScope {
        // Actually, selection.getDocumentIds() takes care of sorting.
        // Consider this an integration test.
        ret.items.map(_.id) must beEqualTo(Seq(doc2.id, doc3.id, doc1.id))
      }

      "return the correct pageInfo" in new IndexScope {
        ret.pageInfo.total must beEqualTo(3) // the list of IDs
        ret.pageInfo.offset must beEqualTo(0)
        ret.pageInfo.limit must beEqualTo(1000)
      }

      "omit text when includeText=false" in new IndexScope {
        override val includeText = false
        ret.items.map(_.text) must beEqualTo(Seq("", "", ""))
      }

      "include text when includeText=true" in new IndexScope {
        override val includeText = true
        ret.items.map(_.text) must not(beEqualTo(Seq("", "", "")))
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
        val storeObjectIds: Seq[Long] = Seq()
        val tagged: Option[Boolean] = None
        val q: Option[Query] = None

        def request = SelectionRequest(
          documentSetId=documentSet.id,
          documentIds=documentIds,
          tagIds=tagIds,
          nodeIds=nodeIds,
          storeObjectIds=storeObjectIds,
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
        override val q = Some(PhraseQuery("moo"))
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
        override val q = Some(PhraseQuery("oneandthree"))
        ret must beEqualTo(Seq(doc1.id))
      }

      "search by nodeIds" in new IndexIdsScope {
        val node = factory.node()
        val nd1 = factory.nodeDocument(node.id, doc1.id)
        val nd2 = factory.nodeDocument(node.id, doc2.id)

        override val nodeIds = Seq(node.id)
        ret must beEqualTo(Seq(doc2.id, doc1.id))
      }

      "search by documentIds" in new IndexIdsScope {
        override val documentIds = Seq(doc1.id)
        ret must beEqualTo(Seq(doc1.id))
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

      "search by storeObjectIds" in new IndexIdsScope {
        val apiToken = factory.apiToken(documentSetId=Some(documentSet.id))
        val view = factory.view(documentSetId=documentSet.id, apiToken=apiToken.token)
        val store = factory.store(apiToken=apiToken.token)
        val obj = factory.storeObject(storeId=store.id)
        val dvo1 = factory.documentStoreObject(doc1.id, obj.id)
        val dvo2 = factory.documentStoreObject(doc2.id, obj.id)

        override val storeObjectIds = Seq(obj.id)
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

      "show a document when the document set ID is not specified" in new ShowScope {
        val doc = await(backend.show(documentId))
        doc must beSome { d: Document =>
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
