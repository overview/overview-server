package controllers.api

import scala.concurrent.Future

import controllers.backend.{SavedSearchBackend, SavedSearchDocumentBackend}
import models.pagination.Page
import org.overviewproject.tree.orm.SearchResult
import org.overviewproject.models.DocumentInfo

class SavedSearchDocumentControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockSearchBackend = mock[SavedSearchBackend]
    val mockBackend = mock[SavedSearchDocumentBackend]
    val controller = new SavedSearchDocumentController {
      override val backend = mockBackend
      override val searchBackend = mockSearchBackend
    }
  }

  "SavedSearchDocumentController" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSetId = 1L
        val query = "foo"
        def searchResult: Option[SearchResult] = Some(factory.searchResult(documentSetId=documentSetId, query=query))
        def documents: Page[DocumentInfo] = Page(Seq())
        override def action = controller.index(documentSetId, query)

        mockSearchBackend.show(documentSetId, query) returns Future(searchResult)
        mockBackend.index(any[Long]) returns Future(documents) // calls documents() later
      }

      "return JSON with status code 200" in new IndexScope {
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
      }

      "return 404 when the SearchResult is not found" in new IndexScope {
        override def searchResult = None
        status(result) must beEqualTo(NOT_FOUND)
        contentAsString(result) must /("message" -> """Search not found. You need to POST to /api/v1/document-sets/:id/search before you can GET the list of documents for that search.""")
      }

      "return [] when there are no documents" in new IndexScope {
        override def documents = Page(Seq())
        contentAsString(result) must /("pagination") /("total" -> 0)
      }

      "return some Documents when there are some" in new IndexScope {
        override def documents = Page(Seq(
          factory.document(title="title", url=Some("http://example.org")).toDocumentInfo,
          factory.document(title="title2").toDocumentInfo
        ))
        val json = contentAsString(result)
        json must /("items") /#(0) /("title" -> "title")
        json must /("items") /#(0) /("url" -> "http://example.org")
        json must /("items") /#(1) /("title" -> "title2")
      }
    }
  }
}
