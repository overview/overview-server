package controllers.api

import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.DocumentBackend
import models.pagination.{Page,PageInfo,PageRequest}
import org.overviewproject.models.DocumentInfo

class DocumentControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends ApiControllerScope {
    val mockBackend = mock[DocumentBackend]
    val controller = new DocumentController {
      override val backend = mockBackend
    }
  }

  "DocumentController" should {
    "#index" should {
      trait IndexScope extends BaseScope {
        val documentSetId = 1L
        val q = ""
        val fields = ""
        val pageRequest = PageRequest(0, 1000)
        def emptyPage[T] = Page(Seq[T](), PageInfo(pageRequest, 0))

        override def action = controller.index(documentSetId, q, fields)
      }

      "return JSON with status code 200" in new IndexScope {
        mockBackend.index(any, any, any) returns Future.successful(emptyPage[DocumentInfo])
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
      }

      "return an empty Array when there are no Documents" in new IndexScope {
        mockBackend.index(any, any, any) returns Future.successful(emptyPage[DocumentInfo])
        contentAsString(result) must /("pagination") /("total" -> 0)
      }

      "grab documentSetId, q and pageRequest from the HTTP request" in new IndexScope {
        override lazy val request = fakeRequest("GET", "/?offset=1&limit=2")
        mockBackend.index(any, any, any) returns Future.successful(emptyPage[DocumentInfo])
        status(result) must beEqualTo(OK)
        there was one(mockBackend).index(documentSetId, q, PageRequest(1, 2))
      }

      "return a JSON error when the fields parameter is not empty or id" in new IndexScope {
        override val fields = "foobar"
        status(result) must beEqualTo(BAD_REQUEST)
        contentType(result) must beSome("application/json")
        contentAsString(result) must /("message" -> """The "fields" parameter must be either "id" or "" for now. Sorry!""")
      }

      "return some Documents when there are Documents" in new IndexScope {
        val documents = Seq(
          factory.document(
            title="foo",
            keywords=Seq("foo", "bar"),
            suppliedId="supplied 1",
            text="text",
            url=Some("http://example.org")
          ),
          factory.document(title="", keywords=Seq(), suppliedId="", url=None)
        ).map(_.toDocumentInfo)
        mockBackend.index(any, any, any) returns Future.successful(
          Page(documents, PageInfo(PageRequest(0, 100), documents.length))
        )

        val json = contentAsString(result)

        json must /("records") /#(0) /("id" -> documents(0).id)
        json must /("records") /#(0) /("title" -> "foo")
        json must /("records") /#(0) /("keywords") /#(0) /("foo")
        json must /("records") /#(0) /("keywords") /#(1) /("bar")
        json must /("records") /#(0) /("suppliedId" -> "supplied 1")
        json must /("records") /#(0) /("url" -> "http://example.org")
        // specs2 foils me on this one:
        // json must not /("records") /#(0) /("text")

        json must /("records") /#(1) /("id" -> documents(1).id)
        json must /("records") /#(1) /("title" -> "")
        // I can't get these past specs2:
        // json must /("records") /#(1) /("keywords" -> beEmpty[Seq[Any]])
        // json must not /("records") /#(1) /("suppliedId")
        // json must not /("records") /#(1) /("url")
        // json must not /("records") /#(1) /("text")
      }

      "return an Array of IDs when fields=id" in new IndexScope {
        override val fields = "id"
        mockBackend.indexIds(documentSetId, q) returns Future(Seq(1L, 2L, 3L))
        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")
        contentAsString(result) must beEqualTo("[1,2,3]")
      }
    }

    "#show" should {
      trait ShowScope extends BaseScope {
        val documentSetId = 1L
        val documentId = 2L

        override def action = controller.show(documentSetId, documentId)
      }

      "return 404 when not found" in new ShowScope {
        mockBackend.show(documentSetId, documentId) returns Future(None)
        status(result) must beEqualTo(NOT_FOUND)
        contentType(result) must beSome("application/json")
        val json = contentAsString(result)
        json must /("message" -> s"Document $documentId not found in document set $documentSetId")
      }

      "return JSON with status code 200" in new ShowScope {
        mockBackend.show(documentSetId, documentId) returns Future(Some(factory.document(
          id=documentId,
          documentSetId=documentSetId,
          keywords=Seq("foo", "bar"),
          url=Some("http://example.org"),
          text="text",
          title="title",
          suppliedId="suppliedId"
        )))

        status(result) must beEqualTo(OK)
        contentType(result) must beSome("application/json")

        val json = contentAsString(result)

        json must /("id" -> documentId)
        json must not /("documentSetId")
        json must /("keywords") /#(0) /("foo")
        json must /("keywords") /#(1) /("bar")
        json must /("url" -> "http://example.org")
        json must /("title" -> "title")
        json must /("text" -> "text")
        json must /("suppliedId" -> "suppliedId")
      }
    }
  }
}
