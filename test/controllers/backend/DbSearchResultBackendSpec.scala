package controllers.backend

import org.specs2.mock.Mockito
import scala.concurrent.Future

import org.overviewproject.jobs.models.Search

class DbSearchBackendSpec extends DbBackendSpecification with Mockito {
  trait BaseScope extends DbScope {
    val backend = new TestDbBackend(session) with DbSearchBackend {
      override val jobQueueSender = mock[controllers.util.JobQueueSender]
    }
  }

  "DbSearchBackend" should {
    "find a document set's search results" in new BaseScope {
      val documentSet = factory.documentSet()
      val searchResult1 = factory.searchResult(documentSetId=documentSet.id)
      val searchResult2 = factory.searchResult(documentSetId=documentSet.id)

      val ret = await(backend.findSearchResults(documentSet.id))
      ret.length must beEqualTo(2)
      ret.map(_.toString) must contain(searchResult1.toString)
      ret.map(_.toString) must contain(searchResult2.toString)
    }

    "not find a different document set's search results" in new BaseScope {
      val documentSet1 = factory.documentSet()
      val documentSet2 = factory.documentSet()
      val searchResult = factory.searchResult(documentSetId=documentSet2.id)

      val ret = await(backend.findSearchResults(documentSet1.id))
      ret.length must beEqualTo(0)
    }

    "find a search result" in new BaseScope {
      val documentSet = factory.documentSet()
      val searchResult = factory.searchResult(documentSetId=documentSet.id, query="query")

      val ret = await(backend.findSearchResult(documentSet.id, "query"))
      ret.map(_.toString) must beSome(searchResult.toString)
    }

    "not find a search result with the wrong document set" in new BaseScope {
      val documentSet1 = factory.documentSet()
      val documentSet2 = factory.documentSet()
      val searchResult = factory.searchResult(documentSetId=documentSet2.id, query="query")

      await(backend.findSearchResult(documentSet1.id, "query")) must beNone
    }

    "not find a search result with the wrong query" in new BaseScope {
      val documentSet = factory.documentSet()
      val searchResult = factory.searchResult(documentSetId=documentSet.id, query="foo")

      await(backend.findSearchResult(documentSet.id, "bar")) must beNone
    }

    "send a Search" in new BaseScope {
      val search = Search(4L, "query")
      backend.jobQueueSender.send(search) returns Future.successful(Unit)
      await(backend.createSearch(search))
      there was one(backend.jobQueueSender).send(search)
    }
  }
}
