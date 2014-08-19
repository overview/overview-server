package controllers.api

import scala.concurrent.Future

import controllers.backend.{SavedSearchBackend, SavedSearchDocumentBackend}
import org.overviewproject.tree.orm.{Document,SearchResult}

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
        def documents: Seq[Document] = Seq()
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
        override def documents = Seq()
        contentAsString(result) must beEqualTo("[]")
      }

      "return some Documents when there are some" in new IndexScope {
        override def documents = Seq(
          factory.document(title=Some("title"), url=Some("http://example.org")),
          factory.document(title=Some("title2"))
        )
        val json = contentAsString(result)
        json must /#(0) /("title" -> "title")
        json must /#(0) /("url" -> "http://example.org")
        json must /#(1) /("title" -> "title2")
      }
    }
  }
}
