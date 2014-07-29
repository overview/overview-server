package controllers.api

import scala.concurrent.Future
import play.api.libs.json.{JsValue,Json}
import play.api.mvc.AnyContentAsJson

import controllers.backend.SearchBackend
import org.overviewproject.jobs.models.Search
import org.overviewproject.tree.orm.SearchResult // FIXME should be models.SearchResult

class SearchControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockBackend = mock[SearchBackend]
    val controller = new SearchController {
      override val backend = mockBackend
    }
  }

  "SearchController" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSetId = 1L
        def searchResults: Seq[SearchResult] = Seq()
        mockBackend.index(documentSetId) returns Future(searchResults) // async, so we get overrides
        override def action = controller.index(documentSetId)
      }

      "return JSON with status code 200" in new IndexScope {
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
      }

      "return an empty Array when there are no search results" in new IndexScope {
        override def searchResults = Seq()
        contentAsString(result) must beEqualTo("[]")
      }

      "return some SearchResults when there are SearchResults" in new IndexScope {
        override def searchResults = Seq(
          factory.searchResult(query="foo", createdAt=new java.sql.Timestamp(1406657946142L)),
          factory.searchResult(query="bar")
        )
        val json = contentAsString(result)
        json must /#(0) /("query" -> "foo")
        json must /#(0) /("state" -> "Complete")
        json must /#(0) /("createdAt" -> "2014-07-29T18:19:06.142Z")
        json must /#(1) /("query" -> "bar")
      }
    }

    "#create" should {
      trait CreateScope extends BaseScope {
        val documentSetId = 1L
        mockBackend.create(any[Search]) returns Future.successful(Unit)
        val body: JsValue
        override def action = controller.create(documentSetId)
        override lazy val result = action(fakeRequest(AnyContentAsJson(body)))
      }

      "return a 400 error when there is no query" in new CreateScope {
        override val body = Json.obj()
        status(result) must beEqualTo(BAD_REQUEST)
        contentAsString(result) must /("message" -> """You must POST an Object like { "query": "foo" }""")
      }

      "create a search" in new CreateScope {
        override val body = Json.obj("query" -> "foo")
        status(result) must beEqualTo(NO_CONTENT)
        there was one(mockBackend).create(Search(documentSetId=1L, query="foo"))
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSetId = 1L
        val query = "foo"
        mockBackend.destroy(any[Long], any[String]) returns Future.successful(Unit)
        override def action = controller.destroy(documentSetId, query)
      }

      "destroy a search" in new DestroyScope {
        status(result) must beEqualTo(NO_CONTENT)
        there was one(mockBackend).destroy(documentSetId, query)
      }
    }

    "#show" should {
      trait ShowScope extends BaseScope {
        val documentSetId = 1L
        val query = "foo"
        def searchResult: Option[SearchResult]
        mockBackend.show(documentSetId, query) returns Future(searchResult) // async
        override def action = controller.show(documentSetId, query)
      }

      "return 404 when not found" in new ShowScope {
        override def searchResult = None
        status(result) must beEqualTo(NOT_FOUND)
      }

      "return 200 when found" in new ShowScope {
        override def searchResult = Some(factory.searchResult(query=query, createdAt=new java.sql.Timestamp(1406657946142L)))
        status(result) must beEqualTo(OK)
        val json = contentAsString(result)
        json must /("query" -> query)
        json must /("createdAt" -> "2014-07-29T18:19:06.142Z")
        json must /("state" -> searchResult.get.state.toString)
      }
    }
  }
}
