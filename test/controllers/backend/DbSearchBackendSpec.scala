package controllers.backend

import org.specs2.mock.Mockito
import scala.concurrent.Future

import org.overviewproject.jobs.models.Search
import org.overviewproject.models.tables.{DocumentSearchResults,SearchResults}

class DbSearchBackendSpec extends DbBackendSpecification with Mockito {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbSearchBackend {
      override val jobQueueSender = mock[controllers.util.JobQueueSender]
    }

    def findSearchResult(id: Long) = {
      import org.overviewproject.database.Slick.simple._
      SearchResults.where(_.id === id).firstOption()(session)
    }

    def findDocumentSearchResult(did: Long, sid: Long) = {
      import org.overviewproject.database.Slick.simple._
      DocumentSearchResults
        .where(_.documentId === did)
        .where(_.searchResultId === sid)
        .firstOption()(session)
    }
  }

  "DbSearchBackend" should {
    "#index" should {
      "index a document set's search results" in new BaseScope {
        val documentSet = factory.documentSet()
        val searchResult1 = factory.searchResult(documentSetId=documentSet.id)
        val searchResult2 = factory.searchResult(documentSetId=documentSet.id)

        val ret = await(backend.index(documentSet.id))
        ret.length must beEqualTo(2)
        ret.map(_.toString) must contain(searchResult1.toString)
        ret.map(_.toString) must contain(searchResult2.toString)
      }

      "not index a different document set's search results" in new BaseScope {
        val documentSet1 = factory.documentSet()
        val documentSet2 = factory.documentSet()
        val searchResult = factory.searchResult(documentSetId=documentSet2.id)

        val ret = await(backend.index(documentSet1.id))
        ret.length must beEqualTo(0)
      }
    }

    "#show" should {
      "show a search result" in new BaseScope {
        val documentSet = factory.documentSet()
        val searchResult = factory.searchResult(documentSetId=documentSet.id, query="query")

        val ret = await(backend.show(documentSet.id, "query"))
        ret.map(_.toString) must beSome(searchResult.toString)
      }

      "not show a search result with the wrong document set" in new BaseScope {
        val documentSet1 = factory.documentSet()
        val documentSet2 = factory.documentSet()
        val searchResult = factory.searchResult(documentSetId=documentSet2.id, query="query")

        await(backend.show(documentSet1.id, "query")) must beNone
      }

      "not show a search result with the wrong query" in new BaseScope {
        val documentSet = factory.documentSet()
        val searchResult = factory.searchResult(documentSetId=documentSet.id, query="foo")

        await(backend.show(documentSet.id, "bar")) must beNone
      }
    }

    "#destroy" should {
      "destroy a search result" in new BaseScope {
        val documentSet = factory.documentSet()
        val searchResult = factory.searchResult(documentSetId=documentSet.id, query="query")

        await(backend.destroy(documentSet.id, "query"))
        findSearchResult(searchResult.id) must beNone
      }

      "destroy a DocumentSearchResult automatically" in new BaseScope {
        val documentSet = factory.documentSet()
        val searchResult = factory.searchResult(documentSetId=documentSet.id, query="query")
        val document = factory.document(documentSetId=documentSet.id)
        factory.documentSearchResult(documentId=document.id, searchResultId=searchResult.id)

        await(backend.destroy(documentSet.id, "query"))
        findDocumentSearchResult(document.id, searchResult.id) must beNone
      }

      "not destroy a search result with the wrong query" in new BaseScope {
        val documentSet = factory.documentSet()
        val searchResult = factory.searchResult(documentSetId=documentSet.id, query="foo")

        await(backend.destroy(documentSet.id, "bar"))
        findSearchResult(searchResult.id) must beSome
      }

      "not destroy a search result with the wrong document set" in new BaseScope {
        val documentSet1 = factory.documentSet()
        val documentSet2 = factory.documentSet()
        val searchResult = factory.searchResult(documentSetId=documentSet2.id, query="query")

        await(backend.destroy(documentSet1.id, "query"))
        findSearchResult(searchResult.id) must beSome
      }
    }

    "#create" should {
      "create a Search" in new BaseScope {
        val search = Search(4L, "query")
        backend.jobQueueSender.send(search) returns Future.successful(Unit)
        await(backend.create(search))
        there was one(backend.jobQueueSender).send(search)
      }
    }
  }
}
